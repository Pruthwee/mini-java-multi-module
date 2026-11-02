#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="minijava-ui"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "=== MiniJava-UI Deploy to AWS EKS ==="
echo ""

# Prompt for AWS region
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
    echo "Error: AWS region is required."
    exit 1
fi

# Prompt for EKS cluster name
read -p "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "Error: EKS cluster name is required."
    exit 1
fi

# Prompt for image URI
read -p "Enter full image URI (registry/repo:tag): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "Error: image URI is required."
    exit 1
fi

# Prompt for environment variables
read -p "Enter value for APP_PORT (or press Enter to skip): " APP_PORT_VAL
APP_PORT_VAL=${APP_PORT_VAL:-8080}

read -p "Enter value for CORS_ORIGIN (or press Enter to skip): " CORS_ORIGIN_VAL
CORS_ORIGIN_VAL=${CORS_ORIGIN_VAL:-*}

# Configure kubectl for the EKS cluster
echo ""
echo "Configuring kubectl for EKS cluster '$CLUSTER_NAME' in region '$AWS_REGION'..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
echo "kubectl configured."
echo ""

# Verify cluster connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || exit 1
echo ""

# Apply the namespace
echo "Applying namespace..."
kubectl apply -f kubernetes/namespace.yaml
echo ""

# No credentials are needed for this application, but the structure is here for future use.
# Create the Secret if needed:
# kubectl create secret generic ${IMAGE_NAME}-secrets -n ${IMAGE_NAME} --from-literal=KEY="VALUE" --dry-run=client -o yaml | kubectl apply -f -

# Apply the deployment with placeholder substitution
echo "Applying deployment..."
cp kubernetes/deployment.yaml kubernetes/deployment.yaml.bak
sed -i.bak 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{NAMESPACE}}|'"${IMAGE_NAME}"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{APP_PORT}}|'"${APP_PORT_VAL}"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{CORS_ORIGIN}}|'"${CORS_ORIGIN_VAL}"'|g' kubernetes/deployment.yaml
kubectl apply -f kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak
echo ""

# Apply the service
echo "Applying service..."
kubectl apply -f kubernetes/service.yaml
echo ""

# Apply the ingress
echo "Applying ingress..."
kubectl apply -f kubernetes/ingress.yaml
echo ""

# Wait for deployment rollout
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/${IMAGE_NAME} -n ${IMAGE_NAME}
echo ""

# Show resources
echo "=== Deployment Summary ==="
kubectl get pods,svc,ingress -n ${IMAGE_NAME}
echo ""

# Show application URL
echo "Application URL: https://${IMAGE_NAME}.example.com"
echo ""
echo "=== Deploy complete ==="
