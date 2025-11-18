#!/bin/bash
set -e

echo "=== Building Application: ${APP_NAME} ==="
echo "Version: ${VERSION}"
echo "Git Tag: ${GIT_TAG}"

# Navigate to app directory
cd apps/${APP_NAME}

# Build Docker image
echo "Building Docker image..."
docker build -t ${DOCKER_REGISTRY}/${APP_NAME}:${VERSION} .
docker tag ${DOCKER_REGISTRY}/${APP_NAME}:${VERSION} ${DOCKER_REGISTRY}/${APP_NAME}:latest

# Push to registry
echo "Pushing to Docker registry..."
docker push ${DOCKER_REGISTRY}/${APP_NAME}:${VERSION}
docker push ${DOCKER_REGISTRY}/${APP_NAME}:latest

echo "=== Build completed successfully ==="
