/**
 * app.js – Controlador de Interface do EscapeKind
 *
 * Responsabilidades:
 *  - Gerir a transição entre ecrãs (início / jogo / fim / erro)
 *  - Renderizar cenas e opções de escolha
 *  - Atualizar a barra de pontuação
 *  - Controlar acessibilidade (Modo Baixo Estímulo, tamanho de fonte)
 *  - Delegar toda a lógica de dados ao EscapeEngine
 */

// ─── Referências ao DOM ──────────────────────────────────────────────────────

const screens = {
  start:   document.getElementById('screen-start'),
  loading: document.getElementById('screen-loading'),
  game:    document.getElementById('screen-game'),
  end:     document.getElementById('screen-end'),
  error:   document.getElementById('screen-error'),
};

const ui = {
  btnStart:        document.getElementById('btn-start'),
  btnRestart:      document.getElementById('btn-restart'),
  btnRetry:        document.getElementById('btn-retry'),
  btnLowStimulus:  document.getElementById('btn-low-stimulus'),
  btnFontUp:       document.getElementById('btn-font-up'),
  btnFontDown:     document.getElementById('btn-font-down'),
  chapterLabel:    document.getElementById('chapter-label'),
  sceneImage:      document.getElementById('scene-image'),
  sceneText:       document.getElementById('scene-text'),
  choicesContainer:document.getElementById('choices-container'),
  scoreFill:       document.getElementById('score-fill'),
  scoreValue:      document.getElementById('score-value'),
  endTitle:        document.getElementById('end-title'),
  endBadge:        document.getElementById('end-badge'),
  endMessage:      document.getElementById('end-message'),
  endScore:        document.getElementById('end-score'),
  errorDetail:     document.getElementById('error-detail'),
};

// ─── Gestão de ecrãs ─────────────────────────────────────────────────────────

function showScreen(name) {
  Object.values(screens).forEach(s => s.classList.remove('active'));
  if (screens[name]) screens[name].classList.add('active');
}

// ─── Fluxo principal ─────────────────────────────────────────────────────────

async function initGame() {
  showScreen('loading');
  try {
    await EscapeEngine.loadNarrative();
    await EscapeEngine.startSession();
    const firstScene = EscapeEngine.getFirstScene();
    EscapeEngine.setCurrentScene(firstScene);
    renderScene(firstScene);
    showScreen('game');
  } catch (err) {
    showError(err.message);
  }
}

function renderScene(scene) {
  // Imagem: fade + leve zoom ao entrar (classe reiniciada a cada cena)
  ui.sceneImage.classList.remove('scene-image--visible');
  ui.sceneImage.src = scene.image || '';
  ui.sceneImage.alt = scene.chapterTitle || '';
  ui.sceneImage.onload = () => {
    requestAnimationFrame(() => ui.sceneImage.classList.add('scene-image--visible'));
  };

  // Texto: renderizado com transição suave
  ui.sceneText.classList.remove('fade-in');
  void ui.sceneText.offsetWidth; // força reflow para reiniciar animação
  ui.sceneText.textContent = scene.text;
  ui.sceneText.classList.add('fade-in');

  // Cabeçalho do capítulo
  ui.chapterLabel.textContent = scene.chapterTitle || '';

  // Opções de escolha (a animação de entrada em sequência é feita via CSS,
  // usando :nth-child no ficheiro de estilos)
  ui.choicesContainer.innerHTML = '';
  scene.choices.forEach((choice) => {
    const btn = document.createElement('button');
    btn.className = 'btn-choice';
    btn.textContent = choice.text;
    btn.setAttribute('data-choice-value', choice.value);
    btn.addEventListener('click', () => handleChoice(scene, choice, btn));
    ui.choicesContainer.appendChild(btn);
  });

  // Atualiza barra de pontuação
  updateScoreBar(EscapeEngine.getTotalScore());
}

async function handleChoice(scene, choice, clickedBtn) {
  // Bloqueia os botões para evitar cliques duplos
  setChoicesEnabled(false);
  if (clickedBtn) clickedBtn.classList.add('btn-choice--selected');

  try {
    await EscapeEngine.registerEvent(scene.id, choice.value);
    updateScoreBar(EscapeEngine.getTotalScore(), true);

    if (choice.next === 'GAME_END') {
      await endGame();
    } else {
      const nextScene = EscapeEngine.getScene(choice.next);
      EscapeEngine.setCurrentScene(nextScene);
      renderScene(nextScene);
    }
  } catch (err) {
    showError(err.message);
  }
}

async function endGame() {
  showScreen('loading');
  try {
    const result = await EscapeEngine.finishSession();
    renderEndScreen(result.finalScore, result.finalReached);
    showScreen('end');
  } catch (err) {
    // Se o servidor falhar, usa os dados locais para mostrar o resultado
    renderEndScreen(EscapeEngine.getTotalScore(), EscapeEngine.getFinalReached());
    showScreen('end');
  }
}

// ─── Ecrã de fim ─────────────────────────────────────────────────────────────

const END_DATA = {
  POSITIVE: {
    title: 'Espectador Ativo',
    badge: '🌟',
    message: 'As tuas escolhas fizeram a diferença. Interviste de forma corajosa e eficaz, demonstrando que um espectador ativo pode interromper o ciclo do bullying. O Pedro e outros como ele precisam de pessoas como tu.',
  },
  NEUTRAL: {
    title: 'Caminho para a Mudança',
    badge: '🤝',
    message: 'Demonstraste empatia e alguma coragem, mas nem sempre a tua intervenção foi suficiente para parar o bullying. Cada pequena ação conta, e conheces agora formas mais eficazes de agir.',
  },
  NEGATIVE: {
    title: 'A Inação Tem Consequências',
    badge: '🪞',
    message: 'A tua passividade permitiu que o bullying continuasse. Ser espectador silencioso alimenta o problema, mesmo sem intenção. Este jogo existe para que possas praticar num ambiente seguro e agir diferente na vida real.',
  },
  IN_PROGRESS: {
    title: 'Jogo Terminado',
    badge: '📖',
    message: 'Chegaste ao fim da narrativa.',
  },
};

function renderEndScreen(score, finalReached) {
  const data = END_DATA[finalReached] || END_DATA['IN_PROGRESS'];
  ui.endTitle.textContent = data.title;
  ui.endBadge.textContent = data.badge;
  ui.endMessage.textContent = data.message;

  // Aplica classe de cor ao badge consoante o desfecho
  ui.endBadge.className = `end-badge end-badge--${finalReached.toLowerCase()}`;

  animateScoreCount(score);
}

/**
 * Anima o número da pontuação final a subir de 0 até ao valor real.
 * Puramente cosmético, respeita o modo baixo estímulo, já que este
 * desativa animações CSS; aqui usamos JS, por isso paramos de imediato
 * se o modo estiver ativo.
 */
function animateScoreCount(finalValue) {
  if (document.body.classList.contains('low-stimulus')) {
    ui.endScore.textContent = finalValue;
    return;
  }
  const durationMs = 600;
  const stepMs = 40;
  const steps = Math.max(1, Math.round(durationMs / stepMs));
  let current = 0;
  const increment = finalValue / steps;

  const timer = setInterval(() => {
    current += increment;
    if (current >= finalValue) {
      ui.endScore.textContent = finalValue;
      clearInterval(timer);
    } else {
      ui.endScore.textContent = Math.round(current);
    }
  }, stepMs);
}

// ─── Utilitários ─────────────────────────────────────────────────────────────

function updateScoreBar(score, pulse = false) {
  const MAX = 9;
  const pct = Math.min(100, Math.round((score / MAX) * 100));
  ui.scoreFill.style.width = `${pct}%`;
  ui.scoreValue.textContent = score;
  ui.scoreFill.closest('[role=progressbar]').setAttribute('aria-valuenow', score);

  if (pulse) {
    ui.scoreFill.classList.remove('score-fill--pulse');
    void ui.scoreFill.offsetWidth;
    ui.scoreFill.classList.add('score-fill--pulse');
  }
}

function setChoicesEnabled(enabled) {
  document.querySelectorAll('.btn-choice').forEach(btn => {
    btn.disabled = !enabled;
  });
}

function showError(detail) {
  ui.errorDetail.textContent = detail || '';
  showScreen('error');
}

// ─── Acessibilidade ──────────────────────────────────────────────────────────

let fontScale = 1.0;

ui.btnLowStimulus.addEventListener('click', () => {
  const isActive = document.body.classList.toggle('low-stimulus');
  ui.btnLowStimulus.setAttribute('aria-pressed', isActive.toString());
  ui.btnLowStimulus.textContent = isActive
    ? 'Modo Baixo Estimulo (Ativado)'
    : 'Modo Baixo Estimulo';
});

ui.btnFontUp.addEventListener('click', () => {
  fontScale = Math.min(fontScale + 0.1, 1.6);
  document.documentElement.style.fontSize = `${fontScale}rem`;
});

ui.btnFontDown.addEventListener('click', () => {
  fontScale = Math.max(fontScale - 0.1, 0.8);
  document.documentElement.style.fontSize = `${fontScale}rem`;
});

// ─── Eventos dos botões ──────────────────────────────────────────────────────

ui.btnStart.addEventListener('click', initGame);

ui.btnRestart.addEventListener('click', () => {
  showScreen('start');
});

ui.btnRetry.addEventListener('click', () => {
  showScreen('start');
});
