# Team A Quickstart: Chime Video Calling + Bedrock Sentiment

This guide is for your current feature branch and focuses only on your scope:
- Chime video call join/end flow
- Bedrock sentiment APIs (text, voice, video, combined)
- Minimal navigation path to test quickly

## 1) Where to run from

- Repo path: D:/dev/2026_spring_careconnect
- Backend path: D:/dev/2026_spring_careconnect/backend/core
- Frontend path: D:/dev/2026_spring_careconnect/frontend

## 2) Start backend (Windows)

Open PowerShell in backend/core and run:

mvnw.cmd spring-boot:run -Dspring.profiles.active=dev

If you use local env files first:

load-env.bat
mvnw.cmd spring-boot:run -Dspring.profiles.active=dev

Backend health/docs:
- http://localhost:8080/actuator/health
- http://localhost:8080/swagger-ui.html

## 3) Backend env vars needed for your feature

Minimum for auth/login:
- JDBC_URI
- DB_USER
- DB_PASSWORD
- SECURITY_JWT_SECRET

For Chime + Bedrock feature path:
- AWS_DEFAULT_REGION (for example us-east-1)
- AWS_ACCESS_KEY_ID
- AWS_SECRET_ACCESS_KEY
- aws.bedrock.sentiment.model-id (optional override, default amazon.nova-pro-v1:0)
- aws.bedrock.voice.model-id (optional override, default mistral.voxtral-small-24b-2507)

Notes:
- In ECS Fargate, use task role/IAM instead of static AWS keys.
- Chime and Bedrock permissions must exist on the role.

## 4) Start frontend

Open a second terminal in frontend and run:

flutter pub get
flutter run -d chrome --web-port=50030 --dart-define=BACKEND_URL=http://localhost:8081

(Use your preferred device instead of chrome if needed. Adjust the port to match your local backend.)

## 5) How to navigate the app (first time)

1. Open app root and go to Login.
2. Log in with an account in your local DB.
3. You should land on /dashboard.
4. For direct Team A testing, use this route in browser:

/#/video-call-chime?userId=1&recipientId=2&userName=Caregiver&recipientName=Patient&initiator=true&video=true&audio=true

What this does:
- Opens HybridVideoCallWidget (Team A path)
- Calls backend /api/v3/calls/{callId}/join
- Uses call sentiment panel (text + periodic combined flow)

## 6) API endpoints in your scope

Base: /api/v3/calls
- POST /{callId}/join
- POST /{callId}/end
- POST /{callId}/sentiment/text
- POST /{callId}/sentiment/voice
- POST /{callId}/sentiment/video
- POST /{callId}/sentiment/combined

## 7) Fast troubleshooting

If call screen opens but fails immediately:
- Verify you are logged in (JWT exists in app storage).
- Verify backend is running on localhost:8080.
- Verify AWS credentials/role and region.

If sentiment calls fail:
- Check backend logs for Bedrock invoke errors.
- Validate IAM permission bedrock:InvokeModel.

If Chime join fails:
- Check backend logs for chime:* permissions and region mismatch.

## 8) ECS Fargate path (parallel, minimal coupling)

Terraform module added at:
- terraform_aws/5_ecs_fargate

Local syntax check already passes:
- terraform init -backend=false
- terraform validate

For team integration later, keep this module parallel and avoid touching shared migration work unless requested.
