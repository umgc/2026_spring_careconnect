# Team A Quickstart: Secure Messaging, Attachments, and Caregiver Messaging Controls

This guide is for your current feature branch and focuses only on the Team A
messaging scope:
- one-to-one patient and caregiver chat
- caregiver-controlled messaging enable/disable
- inbox and unread message indicators
- file, image, and audio attachments
- typing indicators and read receipts

## 1) Where to run from

- Repo path: `D:/dev/2026_spring_careconnect`
- Backend path: `D:/dev/2026_spring_careconnect/backend/core`
- Frontend path: `D:/dev/2026_spring_careconnect/frontend`

## 2) Start backend (Windows)

Open PowerShell in `backend/core` and run:

```powershell
mvnw.cmd spring-boot:run -Dspring.profiles.active=dev
```

If you use local env files first:

```powershell
load-env.bat
mvnw.cmd spring-boot:run -Dspring.profiles.active=dev
```

Backend health/docs:
- `http://localhost:8080/actuator/health`
- `http://localhost:8080/swagger-ui.html`

## 3) Backend env/config needed

Minimum for local messaging flow:
- `JDBC_URI`
- `DB_USER`
- `DB_PASSWORD`
- `SECURITY_JWT_SECRET`

Messaging uses:
- REST APIs under `/v1/api/messages`
- WebSocket endpoint `/ws/chat`
- file download endpoint `/v1/api/files/{fileId}/download`

Local websocket mode must be enabled in local/dev config.

## 4) Start frontend

Open a second terminal in `frontend` and run:

```powershell
flutter pub get
flutter run -d chrome --web-port=50030 --dart-define=BACKEND_URL=http://localhost:8080
```

Use your preferred mobile device or emulator instead of Chrome if needed.

## 5) Test accounts and relationship setup

Messaging is intended for linked caregiver/patient users only.

Before testing, confirm:
1. a patient user exists
2. a caregiver user exists
3. they are linked through `caregiver_patient_link`
4. `patientMessagingEnabled=true` for that link if you want messaging enabled

If the link exists but messaging is disabled:
- patient-side contacts may disappear from contact lists
- chat send/attach controls are disabled in the room
- backend returns `403` for text and attachment sends

## 6) Main user flows

### 6.1 Open the inbox

1. Log in as patient or caregiver.
2. Open the `Messages` tab from bottom navigation.
3. Existing conversations appear in the inbox.
4. Unread conversations show bold text and a blue unread dot.

### 6.2 Start a new conversation

1. Open `Messages`.
2. Tap the contacts icon in the top-right.
3. Select an allowed contact.
4. The chat room opens with the peer display name.

Display name resolution order:
- patient profile first/last name
- caregiver profile first/last name
- `User.name`
- email local-part

### 6.3 Send a text message

1. Type into the message box.
2. Tap `Send`.
3. The message appears immediately in the sender room.
4. The recipient receives it in real time if online.

### 6.4 Send an attachment

Supported UX paths:
- camera / photo library
- file picker
- audio recording

Behavior:
- web: tapping a received attachment downloads it in the browser
- native/mobile: tapping a received attachment downloads it and opens it with the OS app/viewer

### 6.5 Read receipts and typing

Current room behavior:
- typing shows as `X is typing...`
- sent messages show `Sent`
- once delivered to an active peer session they show `Delivered`
- once opened/read in the peer room they show `Read`

## 7) Caregiver messaging enable/disable

### 7.1 Where the caregiver toggles it

1. Log in as caregiver.
2. Open the patient details screen.
3. Find the patient messaging toggle.
4. Turn it on or off.

### 7.2 What happens when disabled

Patient and caregiver room behavior:
- send button is disabled/greyed out
- attachment button is disabled
- text field is disabled
- the room shows a disabled-state banner

Backend enforcement:
- `POST /v1/api/messages/send` returns `403`
- `POST /v1/api/messages/send-attachment` returns `403`

### 7.3 Refresh behavior

If the chat room is already open when the caregiver toggles messaging:
- the room refreshes messaging permission automatically
- the composer should update within a few seconds

## 8) Unread notification behavior

When the user is not currently on the `Messages` tab:
- the bottom-nav `Messages` tab shows a red unread badge count
- the badge updates automatically from inbox unread state

When the user opens the `Messages` tab:
- the badge clears locally

## 9) API endpoints in this feature path

Base: `/v1/api/messages`
- `POST /send`
- `GET /conversation?user1={id}&user2={id}`
- `GET /inbox/{userId}`
- `POST /send-attachment`

Caregiver-patient link messaging control:
- `POST /v1/api/caregiver-patient-links/{linkId}/patient-messaging`

File retrieval:
- `GET /v1/api/files/{fileId}/download`

WebSocket:
- `/ws/chat`

Supported chat socket event types:
- `authenticate`
- `message`
- `message-sent`
- `message-received`
- `typing`
- `user-typing`
- `read-receipt`
- `message-read`

## 10) Database/migrations

This branch includes message persistence migrations:
- `V39__create_messages_table.sql`
- `V40__add_attachment_to_messages.sql`

If your local DB is behind, allow Flyway to apply both on backend startup.

## 11) Fast troubleshooting

If chat opens but no messages send:
- verify the caregiver-patient link exists
- verify `patientMessagingEnabled=true`
- verify the user is logged in and JWT is present

If the chat header or inbox shows email instead of a real name:
- verify the linked patient/caregiver profile row has first and last name
- verify the profile row is linked by `userId`

If typing indicator does not appear:
- verify both users have the room open at the same time
- verify `/ws/chat` is connected in backend logs

If read receipts do not move past `Sent`:
- verify the recipient actually opened the chat room
- verify the recipient websocket connection is active

If web attachments do nothing:
- rebuild or refresh the frontend
- current web behavior is browser download, not in-app preview

If mobile attachments do not open:
- verify the device has an app installed for that file type

If the unread badge does not appear:
- make sure you are not already on the `Messages` tab
- wait a few seconds for the unread badge refresh

## 12) Suggested demo script

1. Log in as caregiver in one browser/device.
2. Log in as patient in another browser/device.
3. Open `Messages` on both sides.
4. Send text both directions.
5. Confirm typing indicator appears.
6. Confirm sent message transitions to `Read`.
7. Send an image or file attachment.
8. Tap the attachment on the recipient side.
9. As caregiver, turn messaging off in patient details.
10. Confirm the patient composer disables automatically.
11. Confirm the `Messages` tab badge appears when a new unread message arrives while off-tab.

## 13) Current scope note

This quickstart covers the Team A messaging implementation currently in this
branch. It is intentionally narrow and does not attempt to document unrelated
social, broadcast, or admin communication features outside this scope.
