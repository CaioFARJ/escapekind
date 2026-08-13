/**
 * engine.js – Motor Narrativo do EscapeKind
 *
 * Responsabilidades:
 *  - Carregar a narrativa JSON
 *  - Gerir o estado local da sessão de jogo
 *  - Persistir o progresso em localStorage e retomar partidas interrompidas
 *  - Navegar entre cenas e capítulos
 *  - Comunicar com a API REST
 *
 * Comunicação com o servidor:
 *  - POST /api/sessions            → inicia sessão (pseudónimo opcional), obtém sessionId
 *  - GET  /api/sessions/:id        → valida uma sessão guardada localmente
 *  - POST /api/events              → regista cada escolha do jogador
 *  - POST /api/sessions/:id/finish → encerra a sessão e obtém o resultado final
 *  - GET  /api/ranking             → obtém o ranking público
 *
 * Anonimato:
 * Nenhuma destas chamadas exige autenticação. A identidade do jogador
 * resume-se ao UUID de sessão gerado pelo servidor e a um pseudónimo
 * opcional, sem qualquer conta ou credencial associada.
 */

const API_BASE = '/api';
const STORAGE_KEY = 'escapekind.session';

/**
 * Estado interno do motor.
 * Nunca exposto diretamente, acedido através das funções abaixo.
 */
let _state = {
  sessionId: null,
  nickname: null,
  narrative: null,          // JSON completo carregado do servidor
  sceneIndex: {},           // Índice plano: sceneId → objeto cena
  currentScene: null,
  totalScore: 0,
  finalReached: 'IN_PROGRESS',
};

// ─── Persistência local ──────────────────────────────────────────────────────

/**
 * Guarda o progresso mínimo necessário para retomar a partida.
 *
 * Guardamos apenas o identificador de sessão, a cena atual e valores de
 * apresentação. A pontuação autoritativa vive sempre no servidor: o valor
 * local serve só para evitar um ecrã vazio enquanto a validação decorre.
 *
 * Falhas de escrita (modo privado, quota esgotada) são silenciosamente
 * ignoradas: a persistência é uma comodidade, não um requisito do jogo.
 */
function saveLocalProgress() {
  if (!_state.sessionId) return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      sessionId: _state.sessionId,
      nickname: _state.nickname,
      sceneId: _state.currentScene ? _state.currentScene.id : null,
      totalScore: _state.totalScore,
      savedAt: new Date().toISOString(),
    }));
  } catch (e) {
    /* localStorage indisponível — o jogo continua normalmente */
  }
}

function readLocalProgress() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

function clearLocalProgress() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch (e) {
    /* nada a fazer */
  }
}

// ─── Carregamento ────────────────────────────────────────────────────────────

/**
 * Carrega a narrativa JSON e constrói o índice plano de cenas.
 */
async function loadNarrative() {
  const resp = await fetch('/narrative.json');
  if (!resp.ok) throw new Error('Não foi possível carregar a narrativa.');
  _state.narrative = await resp.json();

  _state.sceneIndex = {};
  for (const chapter of _state.narrative.chapters) {
    for (const scene of chapter.scenes) {
      _state.sceneIndex[scene.id] = { ...scene, chapterTitle: chapter.title };
    }
  }
}

// ─── API REST ────────────────────────────────────────────────────────────────

/**
 * Cria uma nova sessão no servidor.
 *
 * @param {string|null} nickname pseudónimo opcional escolhido pelo jogador.
 *        O servidor higieniza-o e devolve o valor efetivamente guardado,
 *        que é o que passamos a mostrar na interface.
 */
async function startSession(nickname = null) {
  const resp = await fetch(`${API_BASE}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname: nickname || null }),
  });
  if (!resp.ok) throw new Error(`Erro ao criar sessão: ${resp.status}`);

  const data = await resp.json();
  _state.sessionId = data.sessionId;
  _state.nickname = data.nickname;
  _state.totalScore = 0;
  _state.finalReached = 'IN_PROGRESS';
  saveLocalProgress();
}

/**
 * Tenta retomar uma partida guardada localmente.
 *
 * Valida o sessionId contra o servidor. A sessão só é retomada se ainda
 * existir, continuar em curso e a cena guardada for reconhecida pela
 * narrativa atual (uma narrativa alterada invalida progressos antigos).
 *
 * @returns {object|null} a cena onde retomar, ou null se não houver
 *          progresso válido para recuperar.
 */
async function resumeSession() {
  const saved = readLocalProgress();
  if (!saved || !saved.sessionId || !saved.sceneId) return null;

  let data;
  try {
    const resp = await fetch(`${API_BASE}/sessions/${saved.sessionId}`);
    if (!resp.ok) {          // 404 → sessão já não existe no servidor
      clearLocalProgress();
      return null;
    }
    data = await resp.json();
  } catch (e) {              // servidor inacessível — não arriscamos retomar
    return null;
  }

  if (data.finalReached !== 'IN_PROGRESS') {
    clearLocalProgress();
    return null;
  }

  const scene = _state.sceneIndex[saved.sceneId];
  if (!scene) {
    clearLocalProgress();
    return null;
  }

  _state.sessionId = data.sessionId;
  _state.nickname = data.nickname;
  _state.totalScore = data.safeSupportScore || 0;
  _state.finalReached = data.finalReached;
  _state.currentScene = scene;
  return scene;
}

/**
 * Regista a escolha do jogador no servidor.
 * Atualiza o totalScore local com a resposta do servidor.
 */
async function registerEvent(nodeId, choiceMade, eventType = 'NARRATIVE_CHOICE') {
  if (!_state.sessionId) return;
  const resp = await fetch(`${API_BASE}/events`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      sessionId: _state.sessionId,
      nodeId,
      choiceMade,
      eventType,
    }),
  });
  if (!resp.ok) throw new Error(`Erro ao registar evento: ${resp.status}`);
  const data = await resp.json();
  _state.totalScore = data.totalScore;
  _state.finalReached = data.finalReached;
  saveLocalProgress();
  return data;
}

/**
 * Encerra a sessão no servidor e obtém o resultado final.
 * Ao terminar, o progresso local deixa de ser necessário e é descartado.
 */
async function finishSession() {
  if (!_state.sessionId) return null;
  const resp = await fetch(`${API_BASE}/sessions/${_state.sessionId}/finish`, {
    method: 'POST',
  });
  if (!resp.ok) throw new Error(`Erro ao encerrar sessão: ${resp.status}`);
  const data = await resp.json();
  _state.totalScore = data.finalScore;
  _state.finalReached = data.finalReached;
  clearLocalProgress();
  return data;
}

/**
 * Obtém o ranking público das sessões concluídas.
 * Endpoint sem autenticação — mostrado ao jogador no ecrã de fim de jogo.
 *
 * @param {number} limit número de entradas pretendido (por omissão, 10)
 */
async function fetchRanking(limit = 10) {
  const resp = await fetch(`${API_BASE}/ranking?limit=${limit}`);
  if (!resp.ok) throw new Error(`Erro ao obter ranking: ${resp.status}`);
  return await resp.json();
}

// ─── Navegação ───────────────────────────────────────────────────────────────

/**
 * Obtém a primeira cena da narrativa (início do jogo).
 */
function getFirstScene() {
  return _state.narrative.chapters[0].scenes[0];
}

/**
 * Obtém uma cena pelo ID.
 */
function getScene(id) {
  const scene = _state.sceneIndex[id];
  if (!scene) throw new Error(`Cena não encontrada: ${id}`);
  return scene;
}

/**
 * Atualiza a cena atual no estado e persiste o progresso.
 */
function setCurrentScene(scene) {
  _state.currentScene = scene;
  saveLocalProgress();
}

// ─── Getters de estado ───────────────────────────────────────────────────────

function getCurrentScene() { return _state.currentScene; }
function getTotalScore()    { return _state.totalScore; }
function getFinalReached()  { return _state.finalReached; }
function getSessionId()     { return _state.sessionId; }
function getNickname()      { return _state.nickname; }
function getNarrativeTitle(){ return _state.narrative ? _state.narrative.title : ''; }
function hasSavedProgress() { return readLocalProgress() !== null; }

// ─── Exportação ──────────────────────────────────────────────────────────────
// Motor exposto como objeto global para ser usado por app.js

window.EscapeEngine = {
  loadNarrative,
  startSession,
  resumeSession,
  registerEvent,
  finishSession,
  fetchRanking,
  getFirstScene,
  getScene,
  setCurrentScene,
  getCurrentScene,
  getTotalScore,
  getFinalReached,
  getSessionId,
  getNickname,
  getNarrativeTitle,
  hasSavedProgress,
  clearLocalProgress,
};
