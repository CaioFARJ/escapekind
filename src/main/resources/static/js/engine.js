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
  evidence: [],            // fragmentos de prova recolhidos ate agora
  evidenceQuality: null,   // FORTE | FRACA | NENHUMA, apos o enigma
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
      evidence: _state.evidence,
      evidenceQuality: _state.evidenceQuality,
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
  _state.evidence = [];
  _state.evidenceQuality = null;
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
  _state.evidence = Array.isArray(saved.evidence) ? saved.evidence : [];
  _state.evidenceQuality = saved.evidenceQuality || null;
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
  collectEvidence(scene);
  saveLocalProgress();
}

// ─── Enigma "Reunir Provas" ──────────────────────────────────────────────────

/**
 * Recolhe os fragmentos de prova associados a uma cena.
 *
 * Cada cena de desfecho dos capitulos 1 e 2 deixa dois fragmentos: um que
 * constitui prova utilizavel e outro que nao. A recolha e cumulativa e
 * idempotente — reentrar na mesma cena nao duplica fragmentos.
 */
function collectEvidence(scene) {
  if (!scene || !Array.isArray(scene.evidence)) return;
  for (const frag of scene.evidence) {
    if (!_state.evidence.some(e => e.id === frag.id)) {
      _state.evidence.push({ ...frag });
    }
  }
}

/**
 * Devolve os fragmentos a apresentar no enigma: os recolhidos ao longo do
 * percurso mais os universais definidos na propria cena.
 *
 * A ordem e baralhada para que os fragmentos validos nao apareçam sempre nas
 * mesmas posicoes, o que tornaria o enigma resoluvel sem o ler.
 */
function getPuzzleOptions(scene) {
  const universais = Array.isArray(scene.universalEvidence) ? scene.universalEvidence : [];
  const todos = [..._state.evidence, ...universais];

  for (let i = todos.length - 1; i > 0; i--) {   // baralhamento de Fisher-Yates
    const j = Math.floor(Math.random() * (i + 1));
    [todos[i], todos[j]] = [todos[j], todos[i]];
  }
  return todos;
}

/**
 * Avalia a selecao do jogador e devolve a qualidade do dossie.
 *
 * O criterio nao e a quantidade, e a discriminacao: acertar exige selecionar
 * todos os fragmentos validos disponiveis e nenhum invalido. Assim o enigma
 * funciona em qualquer percurso, incluindo os que recolheram poucas provas.
 *
 *   FORTE   — todos os validos, nenhum invalido
 *   FRACA   — pelo menos um valido, mas incompleto ou com invalidos a mistura
 *   NENHUMA — nenhum valido selecionado, ou nenhum disponivel para recolher
 */
function evaluatePuzzle(options, selectedIds) {
  const validos = options.filter(o => o.valid).map(o => o.id);
  const escolhidosValidos = selectedIds.filter(id => validos.includes(id));
  const escolhidosInvalidos = selectedIds.filter(id => !validos.includes(id));

  if (validos.length === 0) return 'NENHUMA';
  if (escolhidosValidos.length === 0) return 'NENHUMA';
  if (escolhidosValidos.length === validos.length && escolhidosInvalidos.length === 0) {
    return 'FORTE';
  }
  return 'FRACA';
}

/**
 * Submete a resposta ao enigma.
 *
 * O resultado e registado como evento com um choiceMade que o servidor nao
 * reconhece, pelo que nao atribui pontos: o enigma condiciona o desfecho
 * narrativo, nao a Pontuacao de Apoio Seguro. Uma falha de rede nao impede a
 * progressao — o resultado local e valido na mesma.
 */
async function submitPuzzle(scene, options, selectedIds) {
  const quality = evaluatePuzzle(options, selectedIds);
  _state.evidenceQuality = quality;
  saveLocalProgress();

  const detalhe = selectedIds.join(',').slice(0, 200);
  try {
    await registerEvent(scene.id, `EVIDENCE_${quality}|${detalhe}`, 'PUZZLE_ATTEMPT');
  } catch (e) {
    /* o enigma nao pontua: um erro de registo nao deve bloquear o jogo */
  }
  return quality;
}

// ─── Getters de estado ───────────────────────────────────────────────────────

function getCurrentScene() { return _state.currentScene; }
function getTotalScore()    { return _state.totalScore; }
function getFinalReached()  { return _state.finalReached; }
function getSessionId()     { return _state.sessionId; }
function getEvidence()      { return _state.evidence; }
function getEvidenceQuality(){ return _state.evidenceQuality; }
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
  getPuzzleOptions,
  evaluatePuzzle,
  submitPuzzle,
  getCurrentScene,
  getEvidence,
  getEvidenceQuality,
  getTotalScore,
  getFinalReached,
  getSessionId,
  getNickname,
  getNarrativeTitle,
  hasSavedProgress,
  clearLocalProgress,
};
