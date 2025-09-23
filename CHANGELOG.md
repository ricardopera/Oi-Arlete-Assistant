# Changelog

Todas as mudanças notáveis deste projeto serão documentadas aqui.

## [Unreleased]

- Diagnóstico adicional em `AssetExtractor` (contagem/listagem de assets, logs por arquivo)
- Fallbacks de `AudioRecord` no Vosk (fontes e taxas, incluindo 32k), logs detalhados
- Correções de interpolação na UI de Diagnóstico; botão “Reextrair modelos”
- Auto-start do serviço ao abrir o app quando permissão de microfone já concedida
- Documentação inicial (`README.md`)

## [0.1.0] - 2025-09-23

- Integração real: Porcupine (wake) + Vosk (ASR via AAR local)
- Orquestração de estados no `ArleteService`/`ConversationManager`
- Seleção e persistência de entrada de áudio; priorização USB no "Auto"
- Loop do Porcupine com downmix/resample robusto + prevenção de gravadores concorrentes
- Verificações de integridade do modelo Vosk e logs de conteúdo de diretório
