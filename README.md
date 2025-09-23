# OiArlete — Assistente de Voz (Android)

Este repositório contém um app Android (Kotlin/Compose) com um serviço em primeiro plano que oferece:

- Wake word com Picovoice Porcupine
- ASR local com Vosk (AAR local)
- UI de diagnóstico (Android TV) para testar microfone, escolher entrada de áudio e acompanhar logs

Estrutura principal:

- `app/` — aplicativo Android
- `specs/001-assistente-de-voz/` — planos e contratos
- `README.native-engines.md` — instruções dos motores nativos

## Estados e Flavors

- `stub` (padrão): sem libs nativas, apenas simulações
- `porcupineVosk`: ativa motores nativos (BuildConfig.USE_NATIVE_WAKE_ASR = true)

## Como rodar (flavor nativo)

1. Prepare os assets (não versionados):

   - `app/src/main/assets/porcupine_keyword.ppn`
   - `app/src/main/assets/porcupine_params_pt.pv` (ou en/pt-br)
   - `app/src/main/assets/vosk-model/` (diretório de modelo com `final.mdl`, `HCLr.fst`, `Gr.fst`, `ivector/final.dubm`, etc.)

1. Build APK:

   - `./gradlew :app:assemblePorcupineVoskDebug`

1. Instale e execute no device:

   - Conecte o ADB (USB ou Wi‑Fi)
   - Instale o APK e abra o app

## Estado Atual (23/09/2025)

- Serviço `ArleteService` inicia em primeiro plano, detecta assets e extrai para `files/engines/`.
- Wake (Porcupine):
  - Loop manual com `AudioRecord`, fallbacks de fonte/SR, downmix estéreo, reamostragem para 16 kHz, acúmulo de frames.
  - Diagnósticos claros (dispositivo escolhido, SR/canais, fonte, eventos de wake).
- ASR (Vosk):
  - Carrega `org.vosk.Model` a partir de `files/engines/vosk-model`.
  - Melhorias implementadas: fallbacks de fonte/SR, buffer maior, logs detalhados da captura.
  - Instrumentação extra: se o modelo parecer incompleto, lista o conteúdo do diretório para depurar.
- Extração de Assets:
  - `AssetExtractor` corrige paths, reextrai se a pasta estiver incompleta e loga: quantidade em assets, itens copiados e erros por arquivo.
- UI de Diagnóstico:
  - Botão “Reextrair modelos” apaga `files/engines` e reinicia o serviço.
  - “Listar inputs” mostra entradas de áudio; seleção persiste; “Auto” prioriza USB.
  - Mostra eventos e erros do `DiagnosticsBus` no painel.

Problema pendente no device:

- Vosk ainda falha com “Folder …/files/engines/vosk-model does not contain model files” em alguns boots.
- Próximos passos já preparados:
  - Usar os novos logs: “Assets listar 'vosk-model': …” e “Extração dir …: … itens”.
  - Se assets = 0, é empacotamento; se extração destino = 0, é cópia; se destino contém arquivos mas Vosk falha, verificar path/permissões.

## Pastas/arquivos relevantes

- `app/src/main/java/com/example/oiarlete/ArleteService.kt` — orquestra wake/ASR e extração
- `app/src/main/java/com/example/oiarlete/asr/VoskRecognizer.kt` — pipeline do Vosk
- `app/src/main/java/com/example/oiarlete/wake/PorcupineWakeWord.kt` — pipeline do Porcupine
- `app/src/main/java/com/example/oiarlete/engine/AssetExtractor.kt` — cópia dos assets
- `app/src/main/java/com/example/oiarlete/diagnostics/DiagnosticsBus.kt` — bus de logs/UI
- `app/src/main/java/com/example/oiarlete/MainActivity.kt` — painel e botões

## Contribuindo

- Use uma branch de feature para continuar o trabalho (ver `CHANGELOG.md`).
- Commits pequenos e descritivos; mantenha o painel útil para depuração em device.
