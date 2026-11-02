@echo off
setlocal enabledelayedexpansion

set "PROJECT_NAME=minijava-ui"
set "IMAGE_NAME=minijava-ui"

echo === MiniJava-UI Build and Push ===
echo.

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
    echo Error: AWS region is required.
    exit /b 1
)

set /p ECR_REPO="Enter ECR repository name (default: !IMAGE_NAME!): "
if "!ECR_REPO!"=="" set "ECR_REPO=!IMAGE_NAME!"

set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

echo.
echo Select target platform:
echo 1) linux/amd64
echo 2) linux/arm64
echo 3) linux/amd64,linux/arm64 (multi-arch)
set /p PLATFORM_CHOICE="Enter choice (1-3, default 1): "
if "!PLATFORM_CHOICE!"=="2" (
    set "PLATFORM=--platform linux/arm64"
) else if "!PLATFORM_CHOICE!"=="3" (
    set "PLATFORM=--platform linux/amd64,linux/arm64"
) else (
    set "PLATFORM=--platform linux/amd64"
)

REM Get ECR registry URL
for /f "tokens=*" %%i in ('aws ecr describe-registry --region !AWS_REGION! --query registryId --output text 2^>nul') do set "REGISTRY_ID=%%i"
if "!REGISTRY_ID!"=="" (
    echo Error: Could not retrieve ECR registry URL.
    exit /b 1
)
set "REGISTRY_URL=!REGISTRY_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"

set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

echo.
echo Configuration:
echo   AWS Region:    !AWS_REGION!
echo   Registry:      !REGISTRY_URL!
echo   Repository:    !ECR_REPO!
echo   Image Tag:     !IMAGE_TAG!
echo   Full Image:    !FULL_IMAGE_NAME!
echo   Platform:      !PLATFORM!
echo.

echo Logging in to Amazon ECR...
aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
if !ERRORLEVEL! neq 0 (
    echo ECR login failed
    exit /b 1
)
echo ECR login successful.
echo.

echo Checking if ECR repository '!ECR_REPO!' exists...
aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Creating ECR repository '!ECR_REPO!'...
    aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo Failed to create ECR repository
        exit /b 1
    )
)
echo ECR repository ready.
echo.

echo Building Docker image...
docker build !PLATFORM! -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed
    exit /b 1
)
echo Docker build successful.
echo.

echo Pushing Docker image to ECR...
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
    echo Docker push failed
    exit /b 1
)
echo Docker push successful.
echo.

echo === Build and push complete ===
echo Image: !FULL_IMAGE_NAME!
