@echo off
setlocal

REM Wrapper script for regenerating offline SQL/Drift outputs from backend JPA
REM entities.
REM Example:
REM   tool\update-offline-schema.bat Mood,Task,ChatMessage

set ENTITIES=%~1
if "%ENTITIES%"=="" set ENTITIES=Mood,Task

echo Generating offline schema for entities: %ENTITIES%
dart run tool/generate_sql_from_jpa.dart --input ../backend/core/src/main/java/com/careconnect/model --entities %ENTITIES%
if errorlevel 1 (
  echo Failed to generate offline schema.
  exit /b 1
)

echo Done.
