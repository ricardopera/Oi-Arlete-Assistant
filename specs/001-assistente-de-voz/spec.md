# Feature Specification: Android TV Voice Assistant “Arlete” (PT-BR)

**Feature Branch**: `001-assistente-de-voz`  
**Created**: 2025-09-22  
**Status**: Draft  
**Input**: User description: "Construir um assistente de voz para Android TV chamado “Arlete” que: Fica ativo em segundo plano e é “acordado” por uma frase de ativação (“Oi Arlete!”). Após acordar, escuta o usuário por um período curto e entende comandos de voz em português (PT-BR). Executa ações úteis como: Reproduzir música no YouTube Music (abrir app com a música/consulta pedida). Iniciar e manter conversas curtas, respondendo em voz (TTS). Encaminhar comandos para servidores MCP (via HTTP) quando necessário. Decide entre uma resposta rápida local (para comandos simples) e uma resposta remota (para diálogos mais complexos). Mantém um ciclo de conversa: após responder, continua ouvindo por ~15s, podendo receber novos comandos; “parar” encerra a interação e volta ao modo de wake word. Reinicia automaticamente após reboot da TV Box. Por que isso é valioso: Usuários de Android TV podem interagir por voz com baixa fricção (mãos livres), executando ações comuns como tocar música e pedir ajuda rápida. Suporte a PT-BR endereça uma necessidade específica de idioma. Capacidade híbrida (local + remoto) equilibra velocidade, privacidade e qualidade de respostas. Usuários e cenários principais: Usuário diz “Oi Arlete!” em frente à TV, pede “tocar playlist relaxante no YouTube Music”. Usuário pede “qual a previsão do tempo de hoje?” e recebe resposta por voz. Usuário pede algo mais elaborado (“explique X em detalhes”), e o assistente responde com maior contexto. Usuário diz “parar” para encerrar a sessão e voltar ao estado de espera. Requisitos funcionais (testáveis): Detectar com precisão a wake word e confirmar com um sinal sonoro curto. Transcrever fala em PT-BR com baixa latência suficiente para UX fluida. Identificar se o pedido é simples ou complexo e escolher o modo de resposta adequado. Executar intents para abrir o YouTube Music com a consulta do usuário. Enviar comandos a um endpoint MCP e tratar respostas/erros. Gerar áudio TTS em PT-BR e reproduzi-lo imediatamente no dispositivo. Suportar o comando “parar” e o timeout para encerrar o modo de escuta ativa. Inicializar e permanecer rodando após reboot. Critérios de aceitação (exemplos): Dizer “Oi Arlete!” aciona o beep de confirmação e inicia a escuta em < 500ms (meta). “Tocar música X no YouTube Music” abre o app e inicia a reprodução (ou ao menos a busca) de X. Ao perder conectividade com a internet, respostas simples locais continuam funcionando; chamadas remotas falham com feedback de voz adequado. “Parar” encerra a conversa de forma consistente em qualquer ponto da interação. Após reinício do dispositivo, o serviço volta a operar sem intervenção do usuário."

## Execution Flow (main)

```text
1. Parse user description from Input
  → If empty: ERROR "No feature description provided"
2. Extract key concepts from description
  → Identify: actors, actions, data, constraints
3. For each unclear aspect:
  → Mark with [NEEDS CLARIFICATION: specific question]
4. Fill User Scenarios & Testing section
  → If no clear user flow: ERROR "Cannot determine user scenarios"
5. Generate Functional Requirements
  → Each requirement must be testable
  → Mark ambiguous requirements
6. Identify Key Entities (if data involved)
7. Run Review Checklist
  → If any [NEEDS CLARIFICATION]: WARN "Spec has uncertainties"
  → If implementation details found: ERROR "Remove tech details"
8. Return: SUCCESS (spec ready for planning)
```

---

## ⚡ Quick Guidelines

- ✅ Focus on WHAT users need and WHY
- ❌ Avoid HOW to implement (no tech stack, APIs, code structure)
- 👥 Written for business stakeholders, not developers

### Section Requirements

- Mandatory sections: Must be completed for every feature
- Optional sections: Include only when relevant to the feature
- When a section doesn't apply, remove it entirely (don't leave as "N/A")

### For AI Generation

When creating this spec from a user prompt:

1. Mark all ambiguities: Use [NEEDS CLARIFICATION: specific question] for any assumption you'd need to make
2. Don't guess: If the prompt doesn't specify something (e.g., "login system" without auth method), mark it
3. Think like a tester: Every vague requirement should fail the "testable and unambiguous" checklist item
4. Common underspecified areas:

- User types and permissions
- Data retention/deletion policies  
- Performance targets and scale
- Error handling behaviors
- Integration requirements
- Security/compliance needs

---

## User Scenarios & Testing (mandatory)

### Primary User Story

As an Android TV user, I can say “Oi Arlete!” hands‑free to wake the assistant, ask for a command in PT‑BR (e.g., “tocar playlist relaxante no YouTube Music”), and receive immediate confirmation and action (open YouTube Music with the requested query), or a spoken answer when appropriate.

### Acceptance Scenarios

1. Given the TV is idle with the assistant running, When the user says “Oi Arlete!”, Then a short beep plays and the assistant starts listening within < 500ms.
2. Given the assistant is listening, When the user says “tocar música X no YouTube Music”, Then the YouTube Music app opens and starts playing (or at least searching for) X.
3. Given the assistant is listening, When the user asks “qual a previsão do tempo de hoje?”, Then the assistant responds with a short spoken answer in PT‑BR using TTS.
4. Given the request is complex (“explique X em detalhes”), When processing, Then the assistant chooses a remote response path and returns a richer spoken reply.
5. Given internet connectivity is lost, When the user asks a simple local command, Then a local response is provided; And remote calls fail with appropriate spoken feedback.
6. Given the assistant has just responded, When ~15s elapse without a new command, Then the assistant exits active listening and returns to wake‑word mode.
7. Given the assistant is in an active session, When the user says “parar”, Then the session ends and the assistant returns to wake‑word mode consistently.
8. Given the device was rebooted, When the system completes boot, Then the assistant service is running without user intervention.

### Edge Cases

- Wake word false positives from TV speakers or ambient speech.
- Very short/long utterances; silence and background noise.
- YouTube Music not installed or account not signed in.
- TTS language configuration missing or unsupported voices.
- MCP endpoint unreachable, timeouts, or non‑200 responses.
- Multiple rapid commands within the 15s window; overlapping sessions.
- Power constraints or OS killing background processes.

## Requirements (mandatory)

### Functional Requirements

- FR-001: The system MUST detect the wake word “Oi Arlete” precisely and play a short confirmation beep.
- FR-002: The system MUST capture and transcribe PT‑BR speech with latency low enough for a fluid UX.
- FR-003: The system MUST classify requests as simple vs. complex to choose local vs. remote response path.
- FR-004: The system MUST execute an intent to open YouTube Music with the user’s query for playback or search.
- FR-005: The system MUST generate PT‑BR TTS audio and play it immediately on the device.
- FR-006: The system MUST send commands to an MCP HTTP endpoint when needed and handle responses/errors.
- FR-007: The system MUST support the “parar” command to end the active session.
- FR-008: The system MUST auto‑exit active listening after ~15 seconds of inactivity into wake‑word mode.
- FR-009: The system MUST auto‑start and remain running after device reboot.
- FR-010: The system SHOULD continue local simple responses without internet; remote calls MUST fail gracefully with spoken feedback.
- FR-011: The system MUST provide audible confirmation on wake and actionable failures (e.g., network error) in PT‑BR.
- FR-012: The system MUST minimize user friction on Android TV (hands‑free interaction flow maintained).

Additional clarified requirements:

- FR-013: The system MUST call an MCP HTTP endpoint whose base URL is user-configurable (stored in Settings). Authentication via API key header when provided; default timeouts: connect 5s, read 15s.
- FR-014: The system SHOULD meet latency targets: local path (ASR→TTS) p50 ≤ 1.5s; remote path p95 ≤ 4s; ASR accuracy sufficient for common PT‑BR commands.
- FR-015: The system MUST open YouTube Music using ACTION_VIEW with ytmusic:// when available; fallback to https search URL; if app missing, provide spoken feedback to install.
- FR-016: The system MUST synthesize speech using PT‑BR device TTS voice at normal speed, allowing default voice selection by the OS (no custom voice required initially).
- FR-017: The system MUST route simple vs. complex using an initial heuristic (e.g., short imperative requests ≤ 8 tokens and keyword set → local; otherwise remote), with room for iterative improvement.

### Key Entities (include if feature involves data)

- Session: active conversation window with start/end, timeout, and wake‑word state.
- Intent/Command: parsed user intent (play music, query weather, general Q&A).
- MCP Request/Response: payloads exchanged with remote services when choosing remote path.
- Utterance Transcript: text representation of user speech in PT‑BR for routing and action.

---

## Review & Acceptance Checklist

GATE: Automated checks run during main() execution

### Content Quality

- [ ] No implementation details (languages, frameworks, APIs)
- [ ] Focused on user value and business needs
- [ ] Written for non-technical stakeholders
- [ ] All mandatory sections completed

### Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain
- [ ] Requirements are testable and unambiguous  
- [ ] Success criteria are measurable
- [ ] Scope is clearly bounded
- [ ] Dependencies and assumptions identified

---

## Execution Status

Updated by main() during processing

- [ ] User description parsed
- [ ] Key concepts extracted
- [ ] Ambiguities marked
- [ ] User scenarios defined
- [ ] Requirements generated
- [ ] Entities identified
- [ ] Review checklist passed

---
