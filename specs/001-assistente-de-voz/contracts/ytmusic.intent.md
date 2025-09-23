# YouTube Music Intent Contract

## Primary Action

- Action: `android.intent.action.VIEW`
- Data URI: `ytmusic://` (preferred) or `https://music.youtube.com/search?q={query}` as fallback
- Extras: `query` (String) when using package launch fallback
- Package: `com.google.android.apps.youtube.music` (validate installed)

## Expected Behavior

- If a direct play is possible: starts playback for the query
- Else: opens YouTube Music showing search results

## Error Handling

- If app not installed: Fallback to Play Store details or show TTS feedback to install
- If not signed in: Open app normally and inform user via TTS
