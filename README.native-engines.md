# Motores Nativos (Porcupine / Vosk)

Este app permite habilitar wake word (Porcupine) e ASR (Vosk/Whisper) via flavor `porcupineVosk`.

## Ativação por flavor

- Default (stubs): `stub` — não requer assets; funciona com testes unitários.
- Nativo: `porcupineVosk` — habilita BuildConfig.USE_NATIVE_WAKE_ASR = true.

## Assets esperados

- Porcupine: coloque `porcupine_keyword.ppn` em `app/src/main/assets/`.
- Vosk: crie a pasta `app/src/main/assets/vosk-model/` com os arquivos do modelo (adicione um arquivo `README` para detecção inicial).

Os assets não são versionados; avalie o uso de `.gitignore` para protegê-los.

## Build & Test

- Unit tests (stubs):
  - `./gradlew test`
- Unit tests com flavor nativo (sem libs reais, apenas seleção):
  - `./gradlew :app:testPorcupineVoskDebugUnitTest`
- APK com flavor nativo:
  - `./gradlew :app:assemblePorcupineVoskDebug`

## Observações

- O app detecta a presença de assets no `onCreate` do `ArleteService` e só delega para os motores nativos quando ambos: flavor ativo e assets presentes.
- Em falta de assets, volta aos stubs automaticamente.
