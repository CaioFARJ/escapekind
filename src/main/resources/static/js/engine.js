/**
 * engine.js – Motor Narrativo do EscapeKind
 *
 * Responsabilidades:
 *  - Carregar a narrativa JSON
 *  - Gerir o estado local da sessão de jogo
 *  - Navegar entre cenas e capítulos
 *  - Comunicar com a API REST (criar sessão, registar eventos, encerrar sessão)
 *
 * Comunicação com o servidor:
 *  - POST /api/sessions         → inicia sessão, obtém sessionId
 *  - POST /api/events           → regista cada escolha do jogador
 *  - POST /api/sessions/:id/finish → encerra a sessão e obtém resultado final
 */

const API_BASE = '/api';

/**
 * Estado interno do motor.
 * Nunca exposto diretamente — acedido através das funções abaixo.
 */
let _state = {
  sessionId: null,
  narrative: null,          // JSON completo carregado do servidor
  sceneIndex: {},           // Índice plano: sceneId → objeto cena
  currentScene: null,
  totalScore: 0,
  finalReached: 'IN_PROGRESS',
};

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
 * Armazena o sessionId no estado local.
 */
async function startSession() {
  const resp = await fetch(`${API_BASE}/sessions`, { method: 'POST' });
  if (!resp.ok) throw new Error(`Erro ao criar sessão: ${resp.status}`);
  const data = await resp.json();
  _state.sessionId = data.sessionId;
  _state.totalScore = 0;
  _state.finalReached = 'IN_PROGRESS';
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
  return data;
}

/**
 * Encerra a sessão no servidor e obtém o resultado final.
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
  return data;
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
 * Atualiza a cena atual no estado.
 */
function setCurrentScene(scene) {
  _state.currentScene = scene;
}

// ─── Getters de estado ───────────────────────────────────────────────────────

function getCurrentScene() { return _state.currentScene; }
function getTotalScore()    { return _state.totalScore; }
function getFinalReached()  { return _state.finalReached; }
function getSessionId()     { return _state.sessionId; }
function getNarrativeTitle(){ return _state.narrative ? _state.narrative.title : ''; }

// ─── Exportação ──────────────────────────────────────────────────────────────
// Motor exposto como objeto global para ser usado por app.js

window.EscapeEngine = {
  loadNarrative,
  startSession,
  registerEvent,
  finishSession,
  getFirstScene,
  getScene,
  setCurrentScene,
  getCurrentScene,
  getTotalScore,
  getFinalReached,
  getSessionId,
  getNarrativeTitle,
};
