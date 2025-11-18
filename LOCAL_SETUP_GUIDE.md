# Jenkins GitOps Platform - Local Setup Guide (Mac)

## Overview

This guide will help you run the entire Jenkins GitOps platform on your local Mac using:
- **Minikube** or **Kind** - Local Kubernetes cluster
- **Local Docker Registry** - For container images
- **ArgoCD** - GitOps continuous deployment
- **Jenkins** - CI/CD automation

**Estimated Time: 1-1.5 hours**

---

## Prerequisites Setup (15 minutes)

### Step 1: Install Required Tools

```bash
# Install Homebrew (if not already installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Docker Desktop
# Download from: https://www.docker.com/products/docker-desktop
# Or use Homebrew:
brew install --cask docker

# Install kubectl
brew install kubectl

# Install Helm
brew install helm

# Install ArgoCD CLI
brew install argocd

# Install Minikube
brew install minikube

# Alternative: Install Kind (Kubernetes in Docker)
brew install kind

# Install yq (for YAML manipulation)
brew install yq

# Verify installations
docker --version
kubectl version --client
helm version
argocd version
minikube version
```

### Step 2: Start Docker Desktop

```bash
# Open Docker Desktop application
open -a Docker

# Wait for Docker to be running
echo "Waiting for Docker to start..."
while ! docker info > /dev/null 2>&1; do
    sleep 1
done
echo "Docker is running!"
```

---

## Phase 1: Setup Local Kubernetes Cluster (10 minutes)

### Option A: Using Minikube (Recommended)

```bash
# Start Minikube with sufficient resources
minikube start \
  --cpus=4 \
  --memory=8192 \
  --disk-size=40g \
  --driver=docker \
  --kubernetes-version=v1.28.0

# Enable addons
minikube addons enable ingress
minikube addons enable metrics-server
minikube addons enable storage-provisioner

# Verify cluster
kubectl cluster-info
kubectl get nodes
```

### Option B: Using Kind

```bash
# Create Kind cluster configuration
cat > kind-config.yaml <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  kubeadmConfigPatches:
  - |
    kind: InitConfiguration
    nodeRegistration:
      kubeletExtraArgs:
        node-labels: "ingress-ready=true"
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
    protocol: TCP
  - containerPort: 443
    hostPort: 443
    protocol: TCP
  - containerPort: 30000
    hostPort: 30000
    protocol: TCP
  - containerPort: 30001
    hostPort: 30001
    protocol: TCP
- role: worker
- role: worker
EOF

# Create cluster
kind create cluster --name jenkins-gitops --config kind-config.yaml

# Install ingress controller (for Kind)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wait for ingress controller
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s

# Verify cluster
kubectl cluster-info
kubectl get nodes
```

---

## Phase 2: Setup Local Docker Registry (5 minutes)

### Step 3: Create Local Docker Registry

```bash
# Create a local Docker registry
docker run -d \
  -p 5000:5000 \
  --restart=always \
  --name registry \
  registry:2

# For Minikube: Enable registry addon (alternative)
# minikube addons enable registry

# Verify registry is running
curl http://localhost:5000/v2/_catalog

# Test registry
docker pull hello-world
docker tag hello-world localhost:5000/hello-world
docker push localhost:5000/hello-world
docker pull localhost:5000/hello-world
```

---

## Phase 3: Update Project Configuration for Local Setup (10 minutes)

### Step 4: Update Docker Registry References

```bash
# Navigate to project directory
cd /Users/paragkalbande/Documents/jenkins-gitops-argo-k8s

# Update all registry references to localhost:5000
find . -name "*.yaml" -type f -exec sed -i '' 's|your-registry.azurecr.io|localhost:5000|g' {} +

# Verify changes
grep -r "localhost:5000" helm-charts/
grep -r "localhost:5000" environments/
```

### Step 5: Update Storage Class for Local

```bash
# Create local storage class configuration
cat > infra/local/local-storage.yaml <<EOF
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: local-storage
provisioner: kubernetes.io/no-provisioner
volumeBindingMode: WaitForFirstConsumer
---
apiVersion: v1
kind: PersistentVolume
metadata:
  name: jenkins-pv
spec:
  capacity:
    storage: 20Gi
  accessModes:
    - ReadWriteOnce
  persistentVolumeReclaimPolicy: Retain
  storageClassName: local-storage
  hostPath:
    path: /data/jenkins
    type: DirectoryOrCreate
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jenkins-pvc
  namespace: jenkins
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: local-storage
  resources:
    requests:
      storage: 20Gi
EOF

# Create Jenkins namespace first (required for PVC)
kubectl create namespace jenkins

# Apply storage configuration
kubectl apply -f infra/local/local-storage.yaml
```

### Step 6: Create Simplified Jenkins Values for Local

```bash
# Create local Jenkins values
cat > jenkins/helm/values-local.yaml <<EOF
jenkins:
  name: jenkins
  namespace: jenkins
  replicas: 1
  
  image: jenkins/jenkins
  tag: lts-jdk17
  imagePullPolicy: IfNotPresent
  
  serviceAccount: jenkins
  
  javaOpts: "-Xmx1024m -Xms512m -Djenkins.install.runSetupWizard=false"
  
  resources:
    requests:
      cpu: 500m
      memory: 1Gi
    limits:
      cpu: 2000m
      memory: 2Gi
  
  service:
    type: NodePort
    nodePort: 30000
  
  persistence:
    enabled: true
    claimName: jenkins-pvc
    size: 20Gi
    storageClass: local-storage
  
  ingress:
    enabled: false
  
  casc:
    config: |
      jenkins:
        systemMessage: "Jenkins GitOps Platform - Local Development"
        numExecutors: 2
        mode: NORMAL
        securityRealm:
          local:
            allowsSignup: false
        authorizationStrategy:
          loggedInUsersCanDoAnything:
            allowAnonymousRead: false
        clouds:
          - kubernetes:
              name: kubernetes
              serverUrl: https://kubernetes.default
              namespace: jenkins
              jenkinsUrl: http://jenkins:8080
              jenkinsTunnel: jenkins:50000
              containerCapStr: 5
              templates:
                - name: jnlp-agent
                  namespace: jenkins
                  label: jnlp-agent
                  containers:
                    - name: jnlp
                      image: jenkins/inbound-agent:latest
                      workingDir: /home/jenkins/agent
                      ttyEnabled: true
                      resourceRequestCpu: 100m
                      resourceRequestMemory: 128Mi
                      resourceLimitCpu: 500m
                      resourceLimitMemory: 512Mi
      unclassified:
        location:
          url: http://localhost:30000
EOF
```

---

## Phase 4: Setup Git Repository (5 minutes)

### Step 7: Initialize Git Repository

```bash
# Initialize git (if not already done)
git init

# Add all files
git add .

# Commit
git commit -m "Initial commit: Jenkins GitOps Platform - Local Setup"

# Create repository on GitHub
# Option 1: Using GitHub CLI
gh repo create jenkins-gitops-platform --public --source=. --remote=origin --push

# Option 2: Manual
# 1. Go to https://github.com/new
# 2. Create repository named: jenkins-gitops-platform
# 3. Then run:
git remote add origin https://github.com/YOUR_USERNAME/jenkins-gitops-platform.git
git branch -M main
git push -u origin main
```

### Step 8: Update Repository URLs

```bash
# Replace repository URLs in ArgoCD and Jenkins configs
YOUR_GITHUB_USER="YOUR_USERNAME"  # Change this!

# Update ArgoCD files
sed -i '' "s|https://github.com/your-org/jenkins-gitops-platform|https://github.com/$YOUR_GITHUB_USER/jenkins-gitops-platform|g" argocd/*.yaml

# Update Jenkins JCasC files
find jenkins/jcasc -name "*.yaml" -exec sed -i '' "s|https://github.com/your-org/jenkins-gitops-platform|https://github.com/$YOUR_GITHUB_USER/jenkins-gitops-platform|g" {} +

# Commit changes
git add .
git commit -m "Update repository URLs for local setup"
git push
```

---

## Phase 5: Install ArgoCD (10 minutes)

### Step 9: Deploy ArgoCD

```bash
# Create ArgoCD namespace
kubectl create namespace argocd

# Install ArgoCD
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for ArgoCD to be ready
echo "Waiting for ArgoCD to be ready..."
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/name=argocd-server \
  -n argocd \
  --timeout=300s

# Get all ArgoCD pods
kubectl get pods -n argocd
```

### Step 10: Access ArgoCD

```bash
# Get initial admin password
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d)

echo "======================================"
echo "ArgoCD Admin Credentials:"
echo "Username: admin"
echo "Password: $ARGOCD_PASSWORD"
echo "======================================"

# Port-forward ArgoCD server
kubectl port-forward svc/argocd-server -n argocd 8080:443 > /dev/null 2>&1 &

echo "ArgoCD UI: https://localhost:8080"
echo "Note: You may see SSL warnings - it's safe to proceed"

# Login with ArgoCD CLI
argocd login localhost:8080 \
  --username admin \
  --password $ARGOCD_PASSWORD \
  --insecure

# Change admin password (recommended)
argocd account update-password \
  --current-password $ARGOCD_PASSWORD \
  --new-password admin123
```

---

## Phase 6: Deploy Jenkins (15 minutes)

### Step 11: Create Jenkins Service Account and RBAC

```bash
# Note: Jenkins namespace was already created in Step 5
# If you skipped Step 5, create it now:
# kubectl create namespace jenkins

# Create Jenkins service account
kubectl create serviceaccount jenkins -n jenkins

# Create RBAC permissions
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

# Create GitHub token secret (replace with your token)
kubectl create secret generic github-token \
  --from-literal=token=YOUR_GITHUB_TOKEN \
  -n jenkins

# Create dummy secrets for other integrations
kubectl create secret generic docker-registry \
  --from-literal=username=admin \
  --from-literal=password=admin \
  -n jenkins

kubectl create secret generic argocd-token \
  --from-literal=token=dummy-token \
  -n jenkins
```

### Step 12: Deploy Jenkins using Helm

```bash
# Apply storage first
kubectl apply -f infra/local/local-storage.yaml

# Deploy Jenkins with local values
helm install jenkins ./jenkins/helm \
  -f jenkins/helm/values-local.yaml \
  -n jenkins

# Wait for Jenkins to be ready
echo "Waiting for Jenkins to start (this may take 2-3 minutes)..."
kubectl wait --for=condition=ready pod \
  -l app=jenkins \
  -n jenkins \
  --timeout=300s

# Get Jenkins pod status
kubectl get pods -n jenkins
```

### Step 13: Access Jenkins

```bash
# Get Jenkins initial admin password
JENKINS_POD=$(kubectl get pods -n jenkins -l app=jenkins -o jsonpath='{.items[0].metadata.name}')
JENKINS_PASSWORD=$(kubectl exec -n jenkins $JENKINS_POD -- cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo "Jenkins not ready yet")

echo "======================================"
echo "Jenkins Credentials:"
echo "URL: http://localhost:30000"
echo "Username: admin"
echo "Password: $JENKINS_PASSWORD"
echo "======================================"

# If using Minikube, you can also use:
# minikube service jenkins -n jenkins

# For Kind, Jenkins is accessible at: http://localhost:30000
```

---

## Phase 7: Build and Push Applications (15 minutes)

### Step 14: Build Docker Images Locally

```bash
# Navigate to project directory
cd /Users/paragkalbande/Documents/jenkins-gitops-argo-k8s

# Build app1-node
echo "Building app1-node..."
cd apps/app1-node
docker build -t localhost:5000/app1-node:1.0.0 .
docker tag localhost:5000/app1-node:1.0.0 localhost:5000/app1-node:latest
docker push localhost:5000/app1-node:1.0.0
docker push localhost:5000/app1-node:latest

# Build app2-python
echo "Building app2-python..."
cd ../app2-python
docker build -t localhost:5000/app2-python:1.0.0 .
docker tag localhost:5000/app2-python:1.0.0 localhost:5000/app2-python:latest
docker push localhost:5000/app2-python:1.0.0
docker push localhost:5000/app2-python:latest

cd ../..

# Verify images in registry
curl http://localhost:5000/v2/_catalog
```

### Step 15: Configure Image Pull for Local Registry

```bash
# For Minikube: Configure to use local registry
minikube ssh "echo '{ \"insecure-registries\": [\"host.docker.internal:5000\", \"localhost:5000\"] }' | sudo tee /etc/docker/daemon.json"
minikube ssh "sudo systemctl restart docker"

# For Kind: Registry is already accessible
# Kind uses host networking, so localhost:5000 works directly
```

---

## Phase 8: Deploy Applications with ArgoCD (10 minutes)

### Step 16: Create Application Namespaces

```bash
# Create namespaces for each environment
kubectl create namespace dev
kubectl create namespace test
kubectl create namespace stage
kubectl create namespace prod
```

### Step 17: Deploy Applications

```bash
# Create ArgoCD application for app1 in dev
argocd app create app1-dev \
  --repo https://github.com/YOUR_USERNAME/jenkins-gitops-platform.git \
  --path helm-charts/app1 \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace dev \
  --helm-set-file values=environments/dev/app1-values.yaml \
  --sync-policy automated \
  --auto-prune \
  --self-heal

# Create ArgoCD application for app2 in dev
argocd app create app2-dev \
  --repo https://github.com/YOUR_USERNAME/jenkins-gitops-platform.git \
  --path helm-charts/app2 \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace dev \
  --helm-set-file values=environments/dev/app2-values.yaml \
  --sync-policy automated \
  --auto-prune \
  --self-heal

# Wait for sync
echo "Waiting for applications to sync..."
sleep 10

# Check application status
argocd app list
kubectl get pods -n dev
```

### Step 18: Access Applications

```bash
# Port-forward app1
kubectl port-forward svc/app1-node -n dev 3000:80 > /dev/null 2>&1 &
echo "App1 (Node.js): http://localhost:3000"

# Port-forward app2
kubectl port-forward svc/app2-python -n dev 5000:80 > /dev/null 2>&1 &
echo "App2 (Python): http://localhost:5000"

# Test applications
curl http://localhost:3000
curl http://localhost:5000
```

---

## Phase 9: Test CI/CD Pipeline (10 minutes)

### Step 19: Configure Jenkins Credentials

```bash
# Access Jenkins at http://localhost:30000
# Login with credentials from Step 13

# Add credentials manually in Jenkins UI:
# 1. Go to: Manage Jenkins → Credentials → System → Global credentials
# 2. Add GitHub token
# 3. Add Docker registry credentials (username: admin, password: admin)
```

### Step 20: Create and Run Build Pipeline

```bash
# In Jenkins UI:
# 1. Navigate to: New Item
# 2. Name: app1-build
# 3. Type: Pipeline
# 4. Pipeline script from SCM:
#    - SCM: Git
#    - Repository URL: https://github.com/YOUR_USERNAME/jenkins-gitops-platform.git
#    - Script Path: jenkins/pipelines/release-build/Jenkinsfile
# 5. Save and Build

# Monitor build in Jenkins UI
```

---

## Verification & Testing

### Check All Components

```bash
# Check Kubernetes cluster
kubectl cluster-info
kubectl get nodes

# Check namespaces
kubectl get namespaces

# Check ArgoCD
kubectl get pods -n argocd
argocd app list

# Check Jenkins
kubectl get pods -n jenkins
kubectl get svc -n jenkins

# Check applications
kubectl get pods -n dev
kubectl get svc -n dev

# Check local registry
curl http://localhost:5000/v2/_catalog
```

### Access All Services

```bash
echo "======================================"
echo "Service Access URLs:"
echo "======================================"
echo "ArgoCD:        https://localhost:8080"
echo "Jenkins:       http://localhost:30000"
echo "App1 (Node):   http://localhost:3000"
echo "App2 (Python): http://localhost:5000"
echo "Registry:      http://localhost:5000"
echo "======================================"
```

---

## Useful Local Development Commands

### Start/Stop Cluster

```bash
# Minikube
minikube start
minikube stop
minikube delete

# Kind
kind create cluster --name jenkins-gitops
kind delete cluster --name jenkins-gitops
```

### View Logs

```bash
# Jenkins logs
kubectl logs -f -n jenkins -l app=jenkins

# Application logs
kubectl logs -f -n dev -l app=app1-node
kubectl logs -f -n dev -l app=app2-python

# ArgoCD logs
kubectl logs -f -n argocd -l app.kubernetes.io/name=argocd-server
```

### Port Forwarding Helper

```bash
# Create a script to forward all services
cat > forward-all.sh <<'EOF'
#!/bin/bash
kubectl port-forward svc/argocd-server -n argocd 8080:443 &
kubectl port-forward svc/app1-node -n dev 3000:80 &
kubectl port-forward svc/app2-python -n dev 5000:80 &
echo "All services forwarded!"
echo "ArgoCD: https://localhost:8080"
echo "App1: http://localhost:3000"
echo "App2: http://localhost:5000"
echo "Jenkins: http://localhost:30000"
EOF

chmod +x forward-all.sh
./forward-all.sh
```

### Cleanup

```bash
# Delete all applications
argocd app delete app1-dev --yes
argocd app delete app2-dev --yes

# Delete namespaces
kubectl delete namespace dev test stage prod jenkins argocd

# Stop local registry
docker stop registry
docker rm registry

# Delete cluster
minikube delete
# OR
kind delete cluster --name jenkins-gitops
```

---

## Troubleshooting

### Issue: Images not pulling from local registry

```bash
# Ensure registry is accessible
curl http://localhost:5000/v2/_catalog

# For Minikube, configure insecure registry
minikube ssh
echo '{"insecure-registries":["host.docker.internal:5000"]}' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker
exit

# Update image references to use host.docker.internal:5000 for Minikube
```

### Issue: Jenkins pod stuck in pending

```bash
# Check PV/PVC status
kubectl get pv
kubectl get pvc -n jenkins

# Check events
kubectl describe pvc jenkins-pvc -n jenkins

# Delete and recreate storage
kubectl delete -f infra/local/local-storage.yaml
kubectl apply -f infra/local/local-storage.yaml
```

### Issue: ArgoCD can't sync from Git

```bash
# Check repository access
argocd repo list

# Add repository manually
argocd repo add https://github.com/YOUR_USERNAME/jenkins-gitops-platform.git

# Check app sync status
argocd app get app1-dev
```

---

## Summary

**You now have a complete local Jenkins GitOps platform running on your Mac!**

### What's Running:
✅ Local Kubernetes cluster (Minikube/Kind)  
✅ Local Docker registry  
✅ ArgoCD for GitOps  
✅ Jenkins for CI/CD  
✅ Sample applications deployed  

### Next Steps:
1. Explore Jenkins pipelines
2. Make code changes and test CI/CD
3. Promote applications between environments
4. Experiment with GitOps workflows

**Total Setup Time: ~1.5 hours**

---

**Happy coding! 🚀**
