# ================================
# CareConnect ECS Fargate Outputs
# terraform_aws/5_ecs_fargate/outputs.tf
# ================================

output "alb_dns_name" {
  description = "The stable DNS name for the load balancer — use this as your backend URL"
  value       = aws_lb.cc_alb.dns_name
}

output "ecr_repository_url" {
  description = "ECR repository URL — use this when pushing Docker images"
  value       = aws_ecr_repository.cc_backend.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.cc_cluster.name
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = aws_ecs_service.cc_backend.name
}

output "flutter_base_url" {
  description = "HTTP base URL to set as CC_BASE_URL_WEB in Flutter"
  value       = "http://${aws_lb.cc_alb.dns_name}"
}

output "flutter_ws_url" {
  description = "WebSocket base URL to set as CC_WS_URL_WEB in Flutter"
  value       = "ws://${aws_lb.cc_alb.dns_name}"
}

output "cloudwatch_log_group" {
  description = "CloudWatch log group for viewing Spring Boot logs"
  value       = aws_cloudwatch_log_group.cc_backend.name
}
