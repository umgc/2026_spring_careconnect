# ================================
# CareConnect ECS Fargate + ALB
# terraform_aws/5_ecs_fargate/main.tf
#
# Self-contained module — creates all networking and compute.
# Does not depend on other Terraform modules being applied first.
# ================================

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    key = "careconnect/ecs/terraform.tfstate"
  }
}

provider "aws" {
  region = var.primary_region
}

data "aws_caller_identity" "current" {}

# ================================
# VPC AND NETWORKING
# ================================

resource "aws_vpc" "cc_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags = merge(var.default_tags, { Name = "careconnect-vpc" })
}

# Public subnet A — ALB lives here
resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.cc_vpc.id
  cidr_block              = "10.0.3.0/24"
  availability_zone       = "${var.primary_region}a"
  map_public_ip_on_launch = true
  tags = merge(var.default_tags, { Name = "cc-public-subnet-a" })
}

# Public subnet B — ALB requires two AZs
resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.cc_vpc.id
  cidr_block              = "10.0.4.0/24"
  availability_zone       = "${var.primary_region}b"
  map_public_ip_on_launch = true
  tags = merge(var.default_tags, { Name = "cc-public-subnet-b" })
}

# Private subnet A — ECS tasks live here
resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.cc_vpc.id
  cidr_block        = "10.0.1.0/24"
  availability_zone = "${var.primary_region}a"
  tags = merge(var.default_tags, { Name = "cc-private-subnet-a" })
}

# Private subnet B
resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.cc_vpc.id
  cidr_block        = "10.0.2.0/24"
  availability_zone = "${var.primary_region}b"
  tags = merge(var.default_tags, { Name = "cc-private-subnet-b" })
}

# Internet Gateway
resource "aws_internet_gateway" "cc_igw" {
  vpc_id = aws_vpc.cc_vpc.id
  tags   = merge(var.default_tags, { Name = "cc-igw" })
}

# Elastic IP for NAT Gateway
resource "aws_eip" "cc_nat" {
  domain     = "vpc"
  tags       = merge(var.default_tags, { Name = "cc-nat-eip" })
  depends_on = [aws_internet_gateway.cc_igw]
}

# NAT Gateway — allows ECS to reach AWS APIs outbound
resource "aws_nat_gateway" "cc_nat" {
  allocation_id = aws_eip.cc_nat.id
  subnet_id     = aws_subnet.public_a.id
  tags          = merge(var.default_tags, { Name = "cc-nat-gw" })
  depends_on    = [aws_internet_gateway.cc_igw]
}

# Public route table
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.cc_vpc.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.cc_igw.id
  }
  tags = merge(var.default_tags, { Name = "cc-public-rt" })
}

resource "aws_route_table_association" "public_a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

# Private route table
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.cc_vpc.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.cc_nat.id
  }
  tags = merge(var.default_tags, { Name = "cc-private-rt" })
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = aws_subnet.private_a.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}

# ================================
# SECURITY GROUPS
# ================================

resource "aws_security_group" "alb" {
  name   = "cc-apigw-sg"
  vpc_id = aws_vpc.cc_vpc.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.default_tags, { Name = "cc-apigw-sg" })
}

resource "aws_security_group" "ecs" {
  name   = "cc-ecs-sg"
  vpc_id = aws_vpc.cc_vpc.id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.default_tags, { Name = "cc-ecs-sg" })
}

# ================================
# ECR REPOSITORY
# ================================

resource "aws_ecr_repository" "cc_backend" {
  name                 = "careconnect-backend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(var.default_tags, { Name = "careconnect-backend" })
}

resource "aws_ecr_lifecycle_policy" "cc_backend" {
  repository = aws_ecr_repository.cc_backend.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

# ================================
# IAM ROLES
# ================================

resource "aws_iam_role" "ecs_task_role" {
  name = "cc-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.default_tags, { Name = "cc-ecs-task-role" })
}

resource "aws_iam_role_policy" "ecs_task_policy" {
  name = "cc-ecs-task-policy"
  role = aws_iam_role.ecs_task_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "SSMAccess"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
        Resource = "arn:aws:ssm:${var.primary_region}:${data.aws_caller_identity.current.account_id}:parameter/careconnect/*"
      },
      {
        Sid    = "BedrockAccess"
        Effect = "Allow"
        Action = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
        Resource = [
          "arn:aws:bedrock:${var.primary_region}::foundation-model/amazon.nova-pro-v1:0",
          "arn:aws:bedrock:${var.primary_region}::foundation-model/amazon.nova-lite-v1:0",
          "arn:aws:bedrock:${var.primary_region}::foundation-model/mistral.voxtral-small-24b-2507"
        ]
      },
      {
        Sid    = "ChimeAccess"
        Effect = "Allow"
        Action = [
          "chime:CreateMeeting", "chime:DeleteMeeting", "chime:GetMeeting",
          "chime:ListMeetings",  "chime:CreateAttendee", "chime:DeleteAttendee",
          "chime:GetAttendee",   "chime:ListAttendees"
        ]
        Resource = "*"
      },
      {
        Sid    = "S3Access"
        Effect = "Allow"
        Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
        Resource = [
          "arn:aws:s3:::${var.cc_internal_bucket_name}",
          "arn:aws:s3:::${var.cc_internal_bucket_name}/*"
        ]
      },
      {
        Sid      = "CloudWatchLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "*"
      },
      {
        Sid      = "ComprehendAccess"
        Effect   = "Allow"
        Action   = ["comprehend:DetectSentiment", "comprehend:BatchDetectSentiment"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role" "ecs_execution_role" {
  name = "cc-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.default_tags, { Name = "cc-ecs-execution-role" })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_role_policy" {
  role       = aws_iam_role.ecs_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# ================================
# CLOUDWATCH LOGS
# ================================

resource "aws_cloudwatch_log_group" "cc_backend" {
  name              = "/ecs/careconnect-backend"
  retention_in_days = 30
  tags              = merge(var.default_tags, { Name = "cc-backend-logs" })
}

# ================================
# ECS CLUSTER
# ================================

resource "aws_ecs_cluster" "cc_cluster" {
  name = "careconnect-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = merge(var.default_tags, { Name = "careconnect-cluster" })
}

# ================================
# ECS TASK DEFINITION
# ================================

resource "aws_ecs_task_definition" "cc_backend" {
  family                   = "careconnect-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  task_role_arn            = aws_iam_role.ecs_task_role.arn
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn

  container_definitions = jsonencode([{
    name      = "careconnect-backend"
    image     = "${aws_ecr_repository.cc_backend.repository_url}:latest"
    essential = true

    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "AWS_DEFAULT_REGION",     value = var.primary_region },
      { name = "SERVER_PORT",            value = "8080" }
    ]

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 60
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.cc_backend.name
        "awslogs-region"        = var.primary_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  tags = merge(var.default_tags, { Name = "careconnect-backend-task" })
}

# ================================
# APPLICATION LOAD BALANCER
# ================================

resource "aws_lb" "cc_alb" {
  name               = "careconnect-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public_a.id, aws_subnet.public_b.id]
  idle_timeout       = 3600
  tags               = merge(var.default_tags, { Name = "careconnect-alb" })
}

resource "aws_lb_target_group" "cc_backend" {
  name        = "careconnect-backend-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.cc_vpc.id
  target_type = "ip"

  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400
    enabled         = true
  }

  health_check {
    enabled             = true
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 10
    interval            = 30
    matcher             = "200"
  }

  tags = merge(var.default_tags, { Name = "careconnect-backend-tg" })
}

resource "aws_lb_listener" "cc_http" {
  load_balancer_arn = aws_lb.cc_alb.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.cc_backend.arn
  }

  tags = merge(var.default_tags, { Name = "careconnect-http-listener" })
}

# ================================
# ECS SERVICE
# ================================

resource "aws_ecs_service" "cc_backend" {
  name            = "careconnect-backend"
  cluster         = aws_ecs_cluster.cc_cluster.id
  task_definition = aws_ecs_task_definition.cc_backend.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.private_a.id, aws_subnet.private_b.id]
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.cc_backend.arn
    container_name   = "careconnect-backend"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.cc_http]

  tags = merge(var.default_tags, { Name = "careconnect-backend-service" })

  lifecycle {
    ignore_changes = [task_definition]
  }
}
