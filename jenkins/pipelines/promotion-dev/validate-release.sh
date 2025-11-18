#!/bin/bash
set -e

echo "=== Validating Release: ${APP_NAME} ${VERSION} ==="

# Check if image exists in registry
echo "Checking if Docker image exists..."
if docker manifest inspect ${DOCKER_REGISTRY}/${APP_NAME}:${VERSION} > /dev/null 2>&1; then
    echo "✓ Docker image found: ${DOCKER_REGISTRY}/${APP_NAME}:${VERSION}"
else
    echo "✗ Docker image not found: ${DOCKER_REGISTRY}/${APP_NAME}:${VERSION}"
    exit 1
fi

# Validate Helm chart
echo "Validating Helm chart..."
helm lint helm-charts/${APP_NAME}

# Dry-run deployment
echo "Testing Helm deployment (dry-run)..."
helm template ${APP_NAME} helm-charts/${APP_NAME} \
    --set image.tag=${VERSION} \
    --values environments/dev/${APP_NAME}-values.yaml > /dev/null

echo "=== Validation completed successfully ==="
