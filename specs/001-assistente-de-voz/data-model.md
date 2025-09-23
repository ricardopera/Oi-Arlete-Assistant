# Data Model: Arlete Assistant

## Entities

### Session

- id: UUID (runtime only)
- state: enum { Idle, ActiveListening, Speaking }
- startedAt: Instant
- lastInteractionAt: Instant
- timeoutSeconds: Int (default 15)

### UtteranceTranscript

- text: String (PT-BR)
- confidence: Float (0..1)
- durationMs: Int

### IntentCommand

- type: enum { PlayMusic, Query, GeneralQA, Stop }
- query: String
- metadata: Map<String, String>

### McpRequest

- id: UUID
- endpoint: URL
- body: JSON (object)
- timeoutMs: Int (default 15000)

### McpResponse

- id: UUID
- statusCode: Int
- body: JSON (object)
- error: String? (present on failure)

## State Transitions (Session)

- Idle → ActiveListening: on wake word
- ActiveListening → Speaking: on recognized intent needing response
- Speaking → ActiveListening: after TTS completes (loop)
- Any → Idle: on "parar" or timeout

## Validation Rules

- timeoutSeconds ∈ [5, 30]
- McpRequest.timeoutMs ∈ [1000, 60000]
- UtteranceTranscript.text non-empty when confidence ≥ 0.3
