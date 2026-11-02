# DEPLOYMENT

This document describes how to build, deploy, and manage the **MiniJava-UI** backend application on AWS EKS.

## Prerequisites

### System Requirements

- **JDK 17+** (for local builds)
- **Maven** 3.8+ (for local builds)
- **Docker** 20.10+ (for building container images)
- **AWS CLI** 2.x (for ECR and EKS)
- **kubectl** 1.24+ (for Kubernetes)
- **eksctl** 0.140+ (optional, for cluster creation)

### AWS Permissions

The IAM user or role running these scripts needs:

- `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:CompleteLayerUpload`, `ecr:UploadLayerPart`, `ecr:PutLayerPart`, `ecr:InitiateLayerUpload`, `ecr:BatchGetImage`, `ecr:CreateRepository`, `ecr:DescribeRepositories`
- `eks:DescribeCluster` and `eks:ListClusters`
- Kubernetes RBAC permissions for `deployments`, `services`, `ingresses`, `secrets`, `configmaps`, `pods`, and `pods/log` in the target namespace

## Local Setup with Docker Compose

To run the application locally using Docker Compose:

```sh
docker compose up --build
```

The backend API will be available at `http://localhost:8080/`. The health endpoint is at `http://localhost:8080/health`.

Environment variables for local compose:

| Variable | Default | Description |
|---|---|---|
| `APP_PORT` | `8080` | Port the backend listens on |
| `CORS_ORIGIN` | `*` | Allowed origin for CORS (set to your UI origin in production) |

## Building and Pushing to ECR

### Using the shell script (Linux/macOS)

```sh
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script will prompt for:

1. **AWS region** (e.g., `us-east-1`)
2. **ECR repository name** (defaults to `minijava-ui`)
3. **Image tag** (defaults to `latest`)
4. **Target platform** (amd64, arm64, or multi-arch)

It will then:

- Log in to Amazon ECR
- Create the ECR repository if it doesn't exist
- Build the Docker image using the multi-stage Dockerfile
- Push the image to ECR

### Using the batch script (Windows)

```bat
scripts\build-push.bat
```

## Deploying to AWS EKS

### Cluster Setup

If you don't already have an EKS cluster:

```sh
eksctl create cluster --name my-cluster --region us-east-1 --node-type t3.medium --nodes 2
```

### kubectl Configuration

The deploy script configures kubectl automatically, but you can also do it manually:

```sh
aws eks update-kubeconfig --region us-east-1 --name my-cluster
```

### Deploying the Application

```sh
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will prompt for:

1. **AWS region**
2. **EKS cluster name**
3. **Full image URI** (from the ECR push step, e.g., `123456789012.dkr.ecr.us-east-1.amazonaws.com/minijava-ui:latest`)
4. **APP_PORT** value (defaults to `8080`)
5. **CORS_ORIGIN** value (defaults to `*`)

It will then:

- Configure kubectl for the EKS cluster
- Verify cluster connectivity
- Create the namespace
- Create a Kubernetes Secret (for future credentials; none needed currently)
- Apply the deployment, service, and ingress
- Wait for the rollout to complete
- Display the deployed resources and application URL

### Kubernetes Manifests

The following manifests are in the `kubernetes/` directory:

- **namespace.yaml**: Creates the `minijava-ui` namespace
- **deployment.yaml**: Deploys 2 replicas of the backend with resource limits, liveness/readiness probes on `/health`
- **service.yaml**: Exposes the backend via a ClusterIP service on port 80
- **ingress.yaml**: Creates an internet-facing ALB ingress for `minijava-ui.example.com`

### Manual Deployment

```sh
# Set your image URI
IMAGE_URI="123456789012.dkr.ecr.us-east-1.amazonaws.com/minijava-ui:latest"

# Apply manifests with placeholder substitution
kubectl apply -f kubernetes/namespace.yaml
sed -e 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' \
    -e 's|{{NAMESPACE}}|minijava-ui|g' \
    -e 's|{{APP_PORT}}|8080|g' \
    -e 's|{{CORS_ORIGIN}}|*|g' \
    kubernetes/deployment.yaml | kubectl apply -f -
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/minijava-ui -n minijava-ui
```

## Configuration Management

The application reads its configuration from environment variables:

| Variable | Default | Description |
|---|---|---|
| `APP_PORT` | `8080` | Port the backend HTTP server listens on |
| `CORS_ORIGIN` | `*` | Value for the `Access-Control-Allow-Origin` header |

In Kubernetes, these environment variables are set in the `deployment.yaml` env block. The deploy script prompts for their values and substitutes them as `{{APP_PORT}}` and `{{CORS_ORIGIN}}` placeholders before applying. No credentials are needed for this application.

## Security Considerations

- The container runs as a non-root user (`appuser`)
- The base image is `amazoncorretto:17`, a minimal Amazon Linux-based image
- No package managers are installed in the runtime stage
- No `HEALTHCHECK` instruction is used; the platform probes the application's own `/health` endpoint
- CORS is configurable via the `CORS_ORIGIN` environment variable; set it to your frontend origin in production
- The ALB ingress is internet-facing; restrict access via security groups or WAF as needed

## Troubleshooting

### Pod Failures

```sh
# Check pod status
kubectl get pods -n minijava-ui

# Check pod logs
kubectl logs -n minijava-ui -l app=minijava-ui

# Describe problematic pods
kubectl describe pod -n minijava-ui <pod-name>
```

### Service Issues

```sh
# Check service
kubectl get svc -n minijava-ui

# Test connectivity from a temporary pod
kubectl run debug --rm -it --image=curlimages/curl -- curl -s http://minijava-ui-service:80/health
```

### Ingress Problems

```sh
# Check ingress
kubectl get ingress -n minijava-ui

# Check ALB target groups
# (via AWS Console or CLI: aws elbv2 describe-target-groups)
```

### Rollback

```sh
# Rollback to previous revision
kubectl rollout undo deployment/minijava-ui -n minijava-ui
```

## Scaling and Management

### Horizontal Pod Autoscaler

```sh
kubectl autoscale deployment/minijava-ui -n minijava-ui --cpu-percent=70 --min=2 --max=10
```

### Manual Scaling

```sh
kubectl scale deployment/minijava-ui -n minijava-ui --replicas=3
```

### Rolling Updates

```sh
# Update the image
kubectl set image deployment/minijava-ui minijava-ui=<new-image-uri> -n minijava-ui

# Monitor rollout
kubectl rollout status deployment/minijava-ui -n minijava-ui
```

## Java Notes

- **JVM Settings**: The container uses `JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"`. These settings cap the heap at 75% of the container's memory limit (1Gi), with an initial heap of 256Mi. Adjust `JAVA_OPTS` in the deployment env block if you change resource limits.
- **Java Version**: The application targets Java 17 (`maven.compiler.release=17`). The build stage uses `maven:3.9.4-eclipse-temurin-17` and the runtime stage uses `amazoncorretto:17`.
- **Framework**: The application uses the JDK's built-in `com.sun.net.httpserver` — no Spring Boot, Quarkus, or Micronaut. The health endpoint is a simple `GET /health` that returns `{"status":"UP"}`.
- **Packaging**: The backend is packaged as a JAR with a `Main-Class` manifest entry (`com.minijava.ui.Main`), so it is started with `java -jar app.jar`.
- **Multi-module Maven**: The build has two modules (`backend` and `ui`). The container only packages the `backend` module, which provides the API. The `ui` module is a separate static playground.
