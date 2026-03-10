# Wrapper script for regenerating offline SQL/Drift outputs from backend JPA
# entities.
#
# Example:
#   .\tool\update-offline-schema.ps1 "Mood,Task,ChatMessage"
param(
  [string]$Entities = "Mood,Task"
)

$ErrorActionPreference = "Stop"

Write-Host "Generating offline schema for entities: $Entities"

dart run tool/generate_sql_from_jpa.dart `
  --input ../backend/core/src/main/java/com/careconnect/model `
  --entities $Entities

if ($LASTEXITCODE -ne 0) {
  throw "Failed to generate offline schema."
}

Write-Host "Done."
