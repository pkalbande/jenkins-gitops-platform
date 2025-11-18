# Deploy Jenkins with ArgoCD - Setup Guide

This guide explains how to deploy Jenkins using ArgoCD with all required plugins and pre-configured jobs.

## Overview

Jenkins will be deployed with:
- **Release Job**: Builds and publishes Docker images
- **Promotion Orchestrator Job**: Manages environment promotions (dev → test → stage → prod)
- **Required Plugins**: Kubernetes, Pipeline, Git, Promoted Builds, Job DSL, and more
- **Configuration as Code (JCasC)**: Auto-configured Jenkins

---

## Prerequisites

1. **Kind/Minikube cluster** running
2. **Docker registry** on localhost:5000
3. **kubectl** and **argocd** CLI installed
4. **Local storage** configured for Jenkins persistence

---

## Step 1: Prepare Storage

```bash
# Navigate to project directory
cd /Users/paragkalbande/Documents/jenkins-gitops-argo-k8s

# Create Jenkins namespace
kubectl create namespace jenkins

# Create local storage for Jenkins
kubectl apply -f infra/local/local-storage.yaml

# Fix permissions for Kind
docker exec kind-2-control-plane mkdir -p /data/jenkins
docker exec kind-2-control-plane chmod -R 777 /data/jenkins

# Verify storage
kubectl get pv,pvc -n jenkins
```

---

## Step 2: Deploy ArgoCD

```bash
# Create ArgoCD namespace
kubectl create namespace argocd

# Install ArgoCD
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for ArgoCD to be ready
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/name=argocd-server \
  -n argocd \
  --timeout=300s

# Get ArgoCD admin password
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d)

echo "======================================"
echo "ArgoCD Admin Credentials:"
echo "Username: admin"
echo "Password: $ARGOCD_PASSWORD"
echo "======================================"

# Port-forward ArgoCD UI
kubectl port-forward svc/argocd-server -n argocd 8080:443 > /dev/null 2>&1 &

# Login with ArgoCD CLI
argocd login localhost:8080 \
  --username admin \
  --password $ARGOCD_PASSWORD \
  --insecure
```

---

## Step 3: Configure GitHub Token (Optional but Recommended)

```bash
# Create GitHub token secret
# Replace YOUR_GITHUB_TOKEN with your actual token
kubectl create secret generic github-token \
  --from-literal=token=YOUR_GITHUB_TOKEN \
  -n jenkins

# Update jenkins-secrets with GitHub token
kubectl create secret generic jenkins-secrets \
  --from-literal=GITHUB_TOKEN=YOUR_GITHUB_TOKEN \
  --from-literal=DOCKER_USERNAME=admin \
  --from-literal=DOCKER_PASSWORD=admin \
  --from-literal=ARGOCD_TOKEN="" \
  -n jenkins --dry-run=client -o yaml | kubectl apply -f -
```

---

## Step 4: Create Jenkins RBAC

```bash
# Create Jenkins service account
kubectl create serviceaccount jenkins -n jenkins

# Create ClusterRole and ClusterRoleBinding
kubectl apply -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: jenkins
rules:
- apiGroups: [""]
  resources: ["pods", "pods/log", "pods/exec"]
  verbs: ["create", "delete", "get", "list", "patch", "update", "watch"]
- apiGroups: [""]
  resources: ["secrets", "configmaps"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apps"]
  resources: ["deployments"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: jenkins
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: jenkins
subjects:
- kind: ServiceAccount
  name: jenkins
  namespace: jenkins
EOF
```

---

## Step 5: Deploy Jenkins Application via ArgoCD

```bash
# Apply the ArgoCD Application manifest
kubectl apply -f argocd/jenkins-app.yaml

# Check ArgoCD application status
argocd app list

# Watch Jenkins deployment
argocd app get jenkins --watch

# Alternative: Check pods directly
kubectl get pods -n jenkins -w
```

---

## Step 6: Access Jenkins

```bash
# Wait for Jenkins pod to be ready (may take 3-5 minutes for plugin installation)
kubectl wait --for=condition=ready pod \
  -l app=jenkins \
  -n jenkins \
  --timeout=600s

# Port-forward Jenkins UI
kubectl port-forward svc/jenkins -n jenkins 9090:8080 > /dev/null 2>&1 &

echo "======================================"
echo "Jenkins Access:"
echo "URL: http://localhost:9090"
echo "Username: admin"
echo "Password: admin123"
echo "======================================"

# Access Jenkins at: http://localhost:9090
```

---

## Step 7: Verify Jenkins Configuration

Once logged into Jenkins, verify:

### ✅ Installed Plugins

Go to **Manage Jenkins** → **Plugins** → **Installed plugins**

You should see:
- Kubernetes Plugin
- Pipeline Plugins
- Git Plugin
- Configuration as Code
- Promoted Builds
- Job DSL
- Docker Pipeline
- And more...

### ✅ Pre-configured Jobs

You should see two jobs on the dashboard:

1. **release-job**
   - Description: Jenkins Release Job - Build and publish artifacts
   - Parameters: APPLICATION, VERSION, DEPLOY_TO_DEV
   
2. **promotion-orchestrator-job**
   - Description: Promotion Orchestrator Job - Manage environment promotions
   - Parameters: SOURCE_ENV, TARGET_ENV, APPLICATION, VERSION

### ✅ Kubernetes Cloud Configuration

Go to **Manage Jenkins** → **Clouds** → **Kubernetes**

Verify:
- Name: kubernetes
- Kubernetes URL: https://kubernetes.default
- Jenkins URL: http://jenkins:8080
- Pod Templates: jnlp-agent configured

---

## Step 8: Test the Release Job

```bash
# Build app1-node version 1.0.0
# In Jenkins UI:
# 1. Click on "release-job"
# 2. Click "Build with Parameters"
# 3. Select APPLICATION: app1-node
# 4. Enter VERSION: 1.0.0
# 5. Check DEPLOY_TO_DEV: true
# 6. Click "Build"

# Monitor build progress in Jenkins UI
```

The Release Job will:
1. Checkout code from GitHub
2. Validate application structure
3. Build Docker image
4. Run tests
5. Push image to registry (localhost:5000)
6. Create Git tag
7. Update dev environment values
8. Trigger ArgoCD sync

---

## Step 9: Test the Promotion Orchestrator Job

```bash
# Promote app1 from dev to test
# In Jenkins UI:
# 1. Click on "promotion-orchestrator-job"
# 2. Click "Build with Parameters"
# 3. Enter SOURCE_ENV: dev
# 4. Enter TARGET_ENV: test
# 5. Enter APPLICATION: app1
# 6. Enter VERSION: 1.0.0
# 7. Click "Build"

# Monitor promotion progress in Jenkins UI
```

The Promotion Job will:
1. Validate parameters
2. Verify source deployment
3. Run tests
4. Update target environment
5. Trigger ArgoCD sync
6. Verify deployment

---

## Architecture Diagram

```
┌─────────────┐
│   ArgoCD    │
└──────┬──────┘
       │ Deploys
       ▼
┌─────────────┐           ┌──────────────┐
│   Jenkins   │◄──────────│  GitHub Repo │
│   (Pod)     │  Pulls     └──────────────┘
└──────┬──────┘
       │
       ├─► Release Job
       │   ├─ Build Docker Image
       │   ├─ Push to Registry
       │   ├─ Create Git Tag
       │   └─ Update Dev Environment
       │
       └─► Promotion Orchestrator
           ├─ Validate Deployment
           ├─ Run Tests
           ├─ Update Target Env
           └─ Trigger ArgoCD Sync
```

---

## Troubleshooting

### Jenkins pod stuck in Init:0/1

```bash
# Check init container logs
kubectl logs -n jenkins -l app=jenkins -c install-plugins

# If plugins are taking too long, increase timeout
kubectl edit deployment jenkins -n jenkins
# Increase initialDelaySeconds in readinessProbe
```

### Plugins not installed

```bash
# Check Jenkins logs
kubectl logs -n jenkins -l app=jenkins

# Manually install plugins (as fallback)
kubectl exec -n jenkins -it $(kubectl get pod -n jenkins -l app=jenkins -o jsonpath='{.items[0].metadata.name}') -- \
  jenkins-plugin-cli --plugins kubernetes workflow-aggregator git configuration-as-code promoted-builds
```

### Jobs not appearing

```bash
# Check JCasC configuration
kubectl get configmap jenkins-casc-config -n jenkins -o yaml

# Reload configuration in Jenkins UI
# Manage Jenkins → Configuration as Code → Reload existing configuration
```

### Cannot push to Git repository

```bash
# Verify GitHub token
kubectl get secret jenkins-secrets -n jenkins -o jsonpath='{.data.GITHUB_TOKEN}' | base64 -d

# Update token if needed
kubectl create secret generic jenkins-secrets \
  --from-literal=GITHUB_TOKEN=YOUR_NEW_TOKEN \
  --from-literal=DOCKER_USERNAME=admin \
  --from-literal=DOCKER_PASSWORD=admin \
  --from-literal=ARGOCD_TOKEN="" \
  -n jenkins --dry-run=client -o yaml | kubectl apply -f -

# Restart Jenkins
kubectl rollout restart deployment jenkins -n jenkins
```

---

## Cleanup

```bash
# Delete Jenkins application from ArgoCD
argocd app delete jenkins --yes

# Or delete manually
kubectl delete namespace jenkins

# Delete ArgoCD
kubectl delete namespace argocd

# Delete cluster-level resources
kubectl delete clusterrole jenkins
kubectl delete clusterrolebinding jenkins
```

---

## Summary

**You now have Jenkins deployed by ArgoCD with:**

✅ Automated deployment via ArgoCD  
✅ 25+ essential plugins pre-installed  
✅ 2 pre-configured jobs (Release + Promotion)  
✅ Configuration as Code (JCasC)  
✅ Kubernetes agent support  
✅ Git and Docker integration  
✅ GitOps workflow ready  

**Next Steps:**
1. Build applications using Release Job
2. Promote builds between environments
3. Integrate with your CI/CD workflow
4. Add more applications and pipelines

---

**Deployed by ArgoCD, Managed by GitOps! 🚀**
