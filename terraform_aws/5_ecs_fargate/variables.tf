# ================================
# CareConnect ECS Fargate Variables
# terraform_aws/5_ecs_fargate/variables.tf
# ================================

variable "primary_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "default_tags" {
  description = "Tags applied to all resources"
  type        = map(string)
  default = {
    Project     = "CareConnect"
    Environment = "prod"
    Team        = "TeamA"
    ManagedBy   = "Terraform"
  }
}

variable "task_cpu" {
  description = "CPU units for the ECS task (1024 = 1 vCPU)"
  type        = number
  default     = 1024
}

variable "task_memory" {
  description = "Memory in MB for the ECS task"
  type        = number
  default     = 2048
}

variable "cc_internal_bucket_name" {
  description = "Name of the existing S3 bucket for internal storage"
  type        = string
}
