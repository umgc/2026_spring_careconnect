# Team A Quickstart: Chime Video Calling + Bedrock Sentiment + Call Recording

This guide is for your current feature branch and focuses only on your scope:
- Chime video call join/end flow
- Bedrock sentiment APIs (text, voice, video, combined)
- Call recording via AWS Chime Media Capture Pipelines → S3
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

To enable call recording (optional, off by default):
- CARECONNECT_RECORDING_ENABLED=true

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
- POST /{callId}/recording/start
- POST /{callId}/recording/stop
- GET  /{callId}/recording
- GET  /{callId}/recording/playback-url
- DELETE /recordings  (dev/local only — purges all recordings from S3 + DB)

## 7) Call recording setup

Recording is OFF by default. To enable locally:

1. Set CARECONNECT_RECORDING_ENABLED=true in your env or application-dev.properties.

2. Add the following IAM permissions to your AWS dev user:

   iam:CreateServiceLinkedRole
     Resource: arn:aws:iam::*:role/aws-service-role/mediapipelines.chime.amazonaws.com/*

   s3:CreateBucket, s3:PutBucketPolicy, s3:PutObject, s3:GetObject, s3:ListBucket, s3:DeleteObject
     Resource: arn:aws:s3:::careconnect-recordings-*  (and :::careconnect-recordings-*/* for object actions)

   chime:CreateMediaCapturePipeline, chime:DeleteMediaCapturePipeline, chime:GetMediaCapturePipeline
     Resource: *

3. Everything else is automatic:
   - The S3 bucket (careconnect-recordings-{accountId}-{region}) is created at startup if absent.
   - The Chime bucket policy is applied at startup on every run (idempotent).
   - The IAM service-linked role AWSServiceRoleForAmazonChimeSDKMediaPipelines is created at
     startup if absent, provided iam:CreateServiceLinkedRole is in your policy.

   IF iam:CreateServiceLinkedRole cannot be added to your user policy, run this once manually
   (any team member, any machine — one-time per AWS account):

     aws iam create-service-linked-role --aws-service-name mediapipelines.chime.amazonaws.com

4. To clean up test recordings after a session, tap "Delete Call History (Dev)" in the patient
   details screen. This wipes all S3 objects under the recordings/ prefix AND all DB records.

## 8) Fast troubleshooting

If call screen opens but fails immediately:
- Verify you are logged in (JWT exists in app storage).
- Verify backend is running on localhost:8080.
- Verify AWS credentials/role and region.

If sentiment calls fail:
- Check backend logs for Bedrock invoke errors.
- Validate IAM permission bedrock:InvokeModel.

If Chime join fails:
- Check backend logs for chime:* permissions and region mismatch.

If recording fails with "service-linked role" error:
- Add iam:CreateServiceLinkedRole to your IAM user policy (see section 7 above), or
- Run: aws iam create-service-linked-role --aws-service-name mediapipelines.chime.amazonaws.com
- Restart the backend — it provisions the role at startup automatically.

If recording fails with "bucket policy does not exist":
- This should never happen after the startup provisioning was added.
- If it does, restart the backend — policy is re-applied on every start.

## 9) ECS Fargate path (parallel, minimal coupling)

Terraform module added at:
- terraform_aws/5_ecs_fargate

Local syntax check already passes:
- terraform init -backend=false
- terraform validate

For team integration later, keep this module parallel and avoid touching shared migration work unless requested.
