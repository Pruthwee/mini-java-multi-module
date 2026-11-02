#!/bin/bash
set -e

PROJECT_NAME="minijava-ui"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "=== MiniJava-UI Build and Push ==="
echo ""

# Prompt for AWS region
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
    echo "Error: AWS region is required."
    exit 1
fi

# Prompt for ECR repository name
read -p "Enter ECR repository name (default: $IMAGE_NAME): " ECR_REPO
ECR_REPO=${ECR_REPO:-$IMAGE_NAME}

# Prompt for image tag
read -p "Enter image tag (default: latest): " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# Prompt for target platform
echo ""
echo "Select target platform:"
echo "1) linux/amd64"
echo "2) linux/arm64"
echo "3) linux/amd64,linux/arm64 (multi-arch)"
read -p "Enter choice (1-3, default 1): " PLATFORM_CHOICE
case "$PLATFORM_CHOICE" in
    2)  PLATFORM="--platform linux/arm64"
        ;;
    3)  PLATFORM="--platform linux/amd64,linux/arm64"
        ;;
    *)  PLATFORM="--platform linux/amd64"
        ;;
esac

# Get ECR registry URL
REGISTRY_URL=$(aws ecr describe-registry --region "$AWS_REGION" --query 'registryId' --output text 2>/dev/null)
if [ -z "$REGISTRY_URL" ]; then
    echo "Error: Could not retrieve ECR registry URL."
    exit 1
fi
REGISTRY_URL="${REGISTRY_URL}.dkr.ecr.${AWS_REGION}.amazonaws.com"

FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"

echo ""
echo "Configuration:"
echo "  AWS Region:    $AWS_REGION"
echo "  Registry:      $REGISTRY_URL"
echo "  Repository:    $ECR_REPO"
echo "  Image Tag:     $IMAGE_TAG"
echo "  Full Image:    $FULL_IMAGE_NAME"
echo "  Platform:      $PLATFORM"
echo ""

# Login to ECR
echo "Logging in to Amazon ECR..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
echo "ECR login successful."
echo ""

# Create ECR repository if it doesn't exist
echo "Checking if ECR repository '$ECR_REPO' exists..."
aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo "Creating ECR repository '$ECR_REPO'..."
    aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
}
echo "ECR repository ready."
echo ""

# Build the Docker image
echo "Building Docker image..."
docker build $PLATFORM -f Dockerfile -t "$FULL_IMAGE_NAME" .
echo "Docker build successful."
echo ""

# Push the Docker image
echo "Pushing Docker image to ECR..."
docker push "$FULL_IMAGE_NAME"
echo "Docker push successful."
echo ""

echo "=== Build and push complete ==="
echo "Image: $FULL_IMAGE_NAME"
