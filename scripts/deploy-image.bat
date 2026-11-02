@echo off
setlocal enabledelayedexpansion

set "PROJECT_NAME=minijava-ui"
set "IMAGE_NAME=minijava-ui"

echo === MiniJava-UI Deploy to AWS EKS ===
echo.

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
    echo Error: AWS region is required.
    exit /b 1
)

set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo Error: EKS cluster name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full image URI (registry/repo:tag): "
if "!IMAGE_URI!"=="" (
    echo Error: image URI is required.
    exit /b 1
)

set /p APP_PORT_VAL="Enter value for APP_PORT (or press Enter to skip): "
if "!APP_PORT_VAL!"=="" set "APP_PORT_VAL=8080"

set /p CORS_ORIGIN_VAL="Enter value for CORS_ORIGIN (or press Enter to skip): "
if "!CORS_ORIGIN_VAL!"=="" set "CORS_ORIGIN_VAL=*"

echo.
echo Configuring kubectl for EKS cluster '!CLUSTER_NAME!' in region '!AWS_REGION!'...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo Failed to configure kubectl
    exit /b 1
)
echo kubectl configured.
echo.

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo Cluster connectivity check failed
    exit /b 1
)
echo.

echo Applying namespace...
kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 (
    echo Failed to apply namespace
    exit /b 1
)
echo.

REM No credentials are needed for this application, but the structure is here for future use.
REM Create the Secret if needed:
REM kubectl create secret generic !IMAGE_NAME!-secrets -n !IMAGE_NAME! --from-literal=KEY="VALUE" --dry-run=client -o yaml | kubectl apply -f -

echo Applying deployment...
copy /Y kubernetes\deployment.yaml kubernetes\deployment.yaml.bak >nul
powershell -Command "(gc kubernetes\deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Out-File -Encoding UTF8 kubernetes\deployment.yaml.tmp"
move /Y kubernetes\deployment.yaml.tmp kubernetes\deployment.yaml >nul
powershell -Command "(gc kubernetes\deployment.yaml) -replace '{{NAMESPACE}}', '!IMAGE_NAME!' | Out-File -Encoding UTF8 kubernetes\deployment.yaml.tmp"
move /Y kubernetes\deployment.yaml.tmp kubernetes\deployment.yaml >nul
powershell -Command "(gc kubernetes\deployment.yaml) -replace '{{APP_PORT}}', '!APP_PORT_VAL!' | Out-File -Encoding UTF8 kubernetes\deployment.yaml.tmp"
move /Y kubernetes\deployment.yaml.tmp kubernetes\deployment.yaml >nul
powershell -Command "(gc kubernetes\deployment.yaml) -replace '{{CORS_ORIGIN}}', '!CORS_ORIGIN_VAL!' | Out-File -Encoding UTF8 kubernetes\deployment.yaml.tmp"
move /Y kubernetes\deployment.yaml.tmp kubernetes\deployment.yaml >nul
kubectl apply -f kubernetes\deployment.yaml
if !ERRORLEVEL! neq 0 (
    echo Failed to apply deployment
    del /Q kubernetes\deployment.yaml.bak
    exit /b 1
)
del /Q kubernetes\deployment.yaml.bak
echo.

echo Applying service...
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 (
    echo Failed to apply service
    exit /b 1
)
echo.

echo Applying ingress...
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 (
    echo Failed to apply ingress
    exit /b 1
)
echo.

echo Waiting for deployment rollout...
kubectl rollout status deployment\!IMAGE_NAME! -n !IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo Deployment rollout failed
    exit /b 1
)
echo.

echo === Deployment Summary ===
kubectl get pods,svc,ingress -n !IMAGE_NAME!
echo.

echo Application URL: https://!IMAGE_NAME!.example.com
echo.
echo === Deploy complete ===
