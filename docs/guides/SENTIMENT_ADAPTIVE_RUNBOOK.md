# Sentiment Adaptive Mode Runbook

## Purpose
This runbook explains how CareConnect sentiment capture mode works in production, where it is configured, and which thresholds are currently hard-coded.

---

## Current Behavior (As Implemented)

### 1) Global startup mode selection
- Startup mode is selected by `CC_SENTIMENT_MODE` in frontend environment files.
- Valid values:
  - `balanced`
  - `realtime`
  - `adaptive`

### 2) Runtime behavior by mode
- `balanced`
  - Target capture interval is 15 seconds.
  - Lower API/Bedrock traffic and cost.
- `realtime`
  - Target capture interval is 6 seconds.
  - Faster sentiment refresh, higher API/Bedrock traffic and cost.
- `adaptive`
  - Starts in realtime behavior.
  - Switches to balanced when runtime pressure is detected.
  - Switches back to realtime after sustained healthy sends.

### 3) What triggers adaptive switching
Adaptive switching is **not based on total concurrent call count** today.

It is based on per-session send health from the patient sender:
- Degrade to balanced when:
  - 2 consecutive failures, or
  - successful sends that are too slow (>2.5s)
- Upgrade back to realtime when:
  - 6 consecutive healthy sends
- Anti-flap cooldown:
  - Minimum 30 seconds between mode switches

### 4) Caregiver visibility
- Caregiver panel shows active sentiment mode tag:
  - `Adaptive · Realtime`
  - `Adaptive · Balanced`
  - `Realtime`
  - `Balanced`

---

## Configuration Locations

### Environment and startup wiring
- `frontend/.env.example`
- `frontend/.env.template`
- `frontend/load-env.bat`
- `frontend/load-env.sh`
- `frontend/startup.sh`

These scripts pass `CC_SENTIMENT_MODE` into Flutter startup as:
- `--dart-define=CARECONNECT_SENTIMENT_MODE=<mode>`

### Adaptive logic implementation
- `frontend/lib/widgets/hybrid_video_call_widget.dart`

### Embed capture interval usage
- `frontend/lib/widgets/chime_meeting_embed.dart`
- `frontend/lib/widgets/chime_meeting_embed_web.dart`

### Sentiment mode metadata propagation
- `frontend/lib/services/video_call_service.dart` (adds `captureMode` in request bodies)
- `backend/core/src/main/java/com/careconnect/controller/CallController.java` (forwards `captureMode` in websocket sentiment updates)
- `frontend/lib/widgets/sentiment_dashboard_widget.dart` (renders caregiver mode label)

---

## Hard-coded Thresholds (Current)

In `frontend/lib/widgets/hybrid_video_call_widget.dart`:
- Realtime interval: `6000ms`
- Balanced interval: `15000ms`
- Degrade on slow send threshold: `2500ms`
- Degrade streak threshold: `2`
- Recover streak threshold: `6`
- Switch cooldown: `30s`

These values are currently code constants, not backend-managed runtime config.

---

## Recommended Ops Starting Policy

- Default mode: `adaptive`
- If backend/Bedrock pressure rises unexpectedly:
  - Temporarily force `balanced` via `CC_SENTIMENT_MODE=balanced`
- During controlled pilot or clinical review sessions:
  - Use `realtime` for selected users only

---

## Capacity Notes

- There is no global “switch at N calls” control currently.
- Adaptive mode protects each session dynamically, but does not enforce a hard concurrency cap.
- For strict fleet-level protection, add backend-level admission control and/or centralized load telemetry policy in a future phase.
