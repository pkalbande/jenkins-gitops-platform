# Jenkins GitOps Platform - Step-by-Step Setup Guide

## Prerequisites Setup

### 1. Install Required Tools

```bash
# Install AWS CLI
curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"
sudo installer -pkg AWSCLIV2.pkg -target /

# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/darwin/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Install eksctl
brew tap weaveworks/tap
brew install weaveworks/tap/eksctl

# Install Helm
brew install helm

# Install ArgoCD CLI
brew install argocd

# Verify installations
aws --version
kubectl version --client
eksctl version
helm version
argocd version
```

### 2. Configure AWS Credentials

```bash
# Configure AWS CLI
aws configure
# Enter:
# - AWS Access Key ID
# - AWS Secret Access Key
# - Default region (e.g., us-east-1)
# - Default output format (json)

# Verify AWS credentials
aws sts get-caller-identity
```

---

## Phase 1: Infrastructure Setup (30-45 minutes)

### Step 1: Create EKS Cluster

```bash
# Navigate to project directory
cd /Users/paragkalbande/Documents/jenkins-gitops-argo-k8s

# IMPORTANT: Update cluster configuration first
# Edit infra/eks/cluster-config.yaml
# - Change region if needed (default: us-east-1)
# - Adjust instance types if needed (default: t3.large)

# Create the EKS cluster (this takes ~15-20 minutes)
eksctl create cluster -f infra/eks/cluster-config.yaml

# Verify cluster creation
kubectl get nodes
kubectl get namespaces
```

**Expected Output:**
```
NAME                                          STATUS   ROLES    AGE   VERSION
ip-10-0-x-x.us-east-1.compute.internal       Ready    <none>   2m    v1.28.x
ip-10-0-x-x.us-east-1.compute.internal       Ready    <none>   2m    v1.28.x
```

### Step 2: Setup EFS for Persistent Storage

```bash
# Create EFS file system via AWS Console or CLI
aws efs create-file-system \
  --region us-east-1 \
  --performance-mode generalPurpose \
  --throughput-mode bursting \
  --tags Key=Name,Value=jenkins-efs \
  --output json

# Note the FileSystemId from output (e.g., fs-12345678)

# Create mount targets for each subnet
# Get VPC ID from EKS cluster
VPC_ID=$(aws eks describe-cluster \
  --name jenkins-gitops-cluster \
  --query 'cluster.resourcesVpcConfig.vpcId' \
  --output text)

# Get subnet IDs
SUBNET_IDS=$(aws ec2 describe-subnets \
  --filters "Name=vpc-id,Values=$VPC_ID" \
  --query 'Subnets[?MapPublicIpOnLaunch==`false`].SubnetId' \
  --output text)

# Create mount target for each private subnet
for SUBNET_ID in $SUBNET_IDS; do
  aws efs create-mount-target \
    --file-system-id fs-XXXXXXXX \
    --subnet-id $SUBNET_ID \
    --security-groups sg-XXXXXXXX
done

# Update EFS configuration files with your EFS ID
# Edit infra/efs/efs-provisioner.yaml
# Replace fs-xxxxxxxxx with your actual EFS ID
# Replace DNS name with: fs-XXXXXXXX.efs.us-east-1.amazonaws.com

# Deploy EFS provisioner
kubectl apply -f infra/efs/efs-provisioner.yaml
kubectl apply -f infra/efs/storage-class.yaml

# Verify EFS provisioner is running
kubectl get pods -n kube-system -l app=efs-provisioner
```

### Step 3: Install AWS Load Balancer Controller

```bash
# Download IAM policy
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.6.0/docs/install/iam_policy.json

# Create IAM policy
aws iam create-policy \
    --policy-name AWSLoadBalancerControllerIAMPolicy \
    --policy-document file://iam_policy.json

# Create IAM service account
eksctl create iamserviceaccount \
  --cluster=jenkins-gitops-cluster \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --attach-policy-arn=arn:aws:iam::ACCOUNT_ID:policy/AWSLoadBalancerControllerIAMPolicy \
  --override-existing-serviceaccounts \
  --approve

# Add EKS Helm repo
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install AWS Load Balancer Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=jenkins-gitops-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller

# Verify controller is running
kubectl get deployment -n kube-system aws-load-balancer-controller
```

---

## Phase 2: ArgoCD Installation (10-15 minutes)

### Step 4: Install ArgoCD

```bash
# Create ArgoCD namespace
kubectl create namespace argocd

# Install ArgoCD
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for all ArgoCD pods to be ready (2-3 minutes)
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/name=argocd-server \
  -n argocd \
  --timeout=300s

# Verify installation
kubectl get pods -n argocd
```

### Step 5: Access ArgoCD UI

```bash
# Get initial admin password
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d)

echo "ArgoCD Admin Password: $ARGOCD_PASSWORD"

# Port-forward to access ArgoCD UI
kubectl port-forward svc/argocd-server -n argocd 8080:443 &

# Open browser to https://localhost:8080
# Login with:
#   Username: admin
#   Password: (from above command)

# OR use ArgoCD CLI
argocd login localhost:8080 \
  --username admin \
  --password $ARGOCD_PASSWORD \
  --insecure
```

---

## Phase 3: Setup Git Repository (5 minutes)

### Step 6: Push Code to GitHub

```bash
# Initialize git repository (if not already done)
cd /Users/paragkalbande/Documents/jenkins-gitops-argo-k8s
git init

# Add all files
git add .

# Commit
git commit -m "Initial commit: Jenkins GitOps Platform"

# Create repository on GitHub (via web or CLI)
# Using GitHub CLI:
gh repo create jenkins-gitops-platform --public --source=. --remote=origin

# Or manually add remote:
git remote add origin https://github.com/YOUR_USERNAME/jenkins-gitops-platform.git

# Push to GitHub
git branch -M main
git push -u origin main
```

### Step 7: Update Repository URLs

```bash
# Update ArgoCD application files with your GitHub repo URL
# Edit these files:
# - argocd/app-of-apps.yaml
# - argocd/jenkins-app.yaml
# Replace: https://github.com/your-org/jenkins-gitops-platform
# With: https://github.com/YOUR_USERNAME/jenkins-gitops-platform

# Also update in Jenkins JCasC files:
# - jenkins/jcasc/jobs/release-job.yaml
# - jenkins/jcasc/jobs/promote-job.yaml
```

---

## Phase 4: Deploy Jenkins (15-20 minutes)

### Step 8: Create Jenkins Namespace and Secrets

```bash
# Create Jenkins namespace
kubectl create namespace jenkins

# Create GitHub token secret
kubectl create secret generic github-token \
  --from-literal=token=YOUR_GITHUB_TOKEN \
  -n jenkins

# Create Docker registry secret (replace with your registry)
kubectl create secret docker-registry docker-registry \
  --docker-server=your-registry.azurecr.io \
  --docker-username=YOUR_USERNAME \
  --docker-password=YOUR_PASSWORD \
  --docker-email=your-email@domain.com \
  -n jenkins

# Create ArgoCD token (generate from ArgoCD UI first)
# Settings -> Accounts -> admin -> Generate Token
kubectl create secret generic argocd-token \
  --from-literal=token=YOUR_ARGOCD_TOKEN \
  -n jenkins

# Create Jenkins service account
kubectl create serviceaccount jenkins -n jenkins

# Create RBAC for Jenkins
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
  resources: ["secrets"]
  verbs: ["get"]
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

### Step 9: Deploy Jenkins via ArgoCD

```bash
# Apply Jenkins ArgoCD application
kubectl apply -f argocd/jenkins-app.yaml

# Watch deployment progress
kubectl get pods -n jenkins -w

# Or use ArgoCD CLI
argocd app sync jenkins
argocd app wait jenkins --health
```

### Step 10: Access Jenkins

```bash
# Get Jenkins initial admin password
JENKINS_POD=$(kubectl get pods -n jenkins -l app=jenkins -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n jenkins $JENKINS_POD -- cat /var/jenkins_home/secrets/initialAdminPassword

# Port-forward to access Jenkins
kubectl port-forward svc/jenkins -n jenkins 8081:8080 &

# Open browser to http://localhost:8081
# Use the password from above to login
```

---

## Phase 5: Configure Container Registry (10 minutes)

### Step 11: Setup Azure Container Registry (ACR)

```bash
# Create ACR (if using Azure)
az acr create \
  --resource-group YOUR_RESOURCE_GROUP \
  --name yourregistry \
  --sku Basic

# Login to ACR
az acr login --name yourregistry

# Get ACR credentials
az acr credential show --name yourregistry

# Update all files with your registry URL:
# - helm-charts/app1/values.yaml
# - helm-charts/app2/values.yaml
# - environments/*/app*-values.yaml
# Replace: your-registry.azurecr.io
# With: yourregistry.azurecr.io
```

**OR for Docker Hub:**

```bash
# Login to Docker Hub
docker login

# Update registry to: docker.io/YOUR_USERNAME
```

---

## Phase 6: Build and Deploy Applications (20 minutes)

### Step 12: Build Application Images

```bash
# Build app1-node
cd apps/app1-node
docker build -t yourregistry.azurecr.io/app1-node:1.0.0 .
docker push yourregistry.azurecr.io/app1-node:1.0.0

# Build app2-python
cd ../app2-python
docker build -t yourregistry.azurecr.io/app2-python:1.0.0 .
docker push yourregistry.azurecr.io/app2-python:1.0.0

cd ../..
```

### Step 13: Deploy Applications with ArgoCD

```bash
# Create application namespaces
kubectl create namespace dev
kubectl create namespace test
kubectl create namespace stage
kubectl create namespace prod

# Create ArgoCD applications for app1 in each environment
argocd app create app1-dev \
  --repo https://github.com/YOUR_USERNAME/jenkins-gitops-platform.git \
  --path helm-charts/app1 \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace dev \
  --values-literal-file ../../environments/dev/app1-values.yaml \
  --sync-policy automated

# Create ArgoCD applications for app2 in each environment
argocd app create app2-dev \
  --repo https://github.com/YOUR_USERNAME/jenkins-gitops-platform.git \
  --path helm-charts/app2 \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace dev \
  --values-literal-file ../../environments/dev/app2-values.yaml \
  --sync-policy automated

# Sync applications
argocd app sync app1-dev
argocd app sync app2-dev

# Check deployment status
kubectl get pods -n dev
argocd app list
```

---

## Phase 7: Test CI/CD Pipeline (15 minutes)

### Step 14: Test Jenkins Pipeline

```bash
# Access Jenkins UI at http://localhost:8081
# Navigate to: Release/app1-release

# Click "Build with Parameters"
# Set:
#   - APP_NAME: app1-node
#   - GIT_TAG: v1.0.0
# Click Build

# Monitor build progress in Jenkins UI
# Build will:
# 1. Checkout code
# 2. Build Docker image
# 3. Push to registry
# 4. Tag release
```

### Step 15: Test Promotion Pipeline

```bash
# In Jenkins UI, navigate to: Promotion/DEV/promote-to-dev

# Click "Build with Parameters"
# Set:
#   - APP_NAME: app1-node
#   - VERSION: 1.0.0
# Click Build

# This will:
# 1. Validate the release
# 2. Update dev/app1-values.yaml
# 3. Commit to Git
# 4. Trigger ArgoCD sync

# Verify deployment
kubectl get pods -n dev
kubectl describe deployment app1-node -n dev
```

---

## Phase 8: Setup Ingress (Optional - 15 minutes)

### Step 16: Configure DNS and SSL

```bash
# Request ACM certificate for your domain
aws acm request-certificate \
  --domain-name "*.yourdomain.com" \
  --validation-method DNS \
  --region us-east-1

# Note the certificate ARN
# Update certificate ARN in:
# - infra/ingress/alb-ingress.yaml
# - environments/stage/*-values.yaml
# - environments/prod/*-values.yaml

# Apply ingress configuration
kubectl apply -f infra/ingress/alb-ingress.yaml

# Get ALB DNS name
kubectl get ingress -n jenkins

# Create CNAME record in Route53 or your DNS provider
# jenkins.yourdomain.com -> ALB DNS name
```

---

## Verification Checklist

### ✅ Infrastructure
```bash
kubectl get nodes                          # Should show 2+ nodes
kubectl get sc                             # Should show efs-sc storage class
kubectl get deployment -n kube-system      # Should show ALB controller
```

### ✅ ArgoCD
```bash
kubectl get pods -n argocd                 # All pods should be Running
argocd app list                            # Should show jenkins and apps
```

### ✅ Jenkins
```bash
kubectl get pods -n jenkins                # Jenkins pod should be Running
kubectl get pvc -n jenkins                 # PVC should be Bound
```

### ✅ Applications
```bash
kubectl get pods -n dev                    # App pods should be Running
kubectl get svc -n dev                     # Services should be available
kubectl get ingress -n dev                 # Ingress should show ADDRESS
```

---

## Troubleshooting Common Issues

### EKS Cluster Creation Fails
```bash
# Check CloudFormation stacks
aws cloudformation describe-stacks --region us-east-1

# Delete failed cluster
eksctl delete cluster --name jenkins-gitops-cluster --region us-east-1
```

### EFS Mount Issues
```bash
# Check security groups allow NFS (port 2049)
# Verify mount targets are in correct subnets
kubectl logs -n kube-system -l app=efs-provisioner
```

### ArgoCD Sync Failures
```bash
# Check application status
argocd app get <app-name>

# View sync errors
argocd app sync <app-name> --dry-run

# Force refresh
argocd app get <app-name> --refresh
```

### Jenkins Pod Not Starting
```bash
# Check pod events
kubectl describe pod -n jenkins <pod-name>

# Check PVC status
kubectl get pvc -n jenkins

# View logs
kubectl logs -n jenkins <pod-name>
```

---

## Next Steps

1. **Configure Jenkins Credentials**: Add GitHub, Docker, AWS credentials in Jenkins UI
2. **Setup Webhooks**: Configure GitHub webhooks to trigger Jenkins builds
3. **Enable Monitoring**: Install Prometheus and Grafana for observability
4. **Setup Backup**: Configure EFS backup policies
5. **Security Hardening**: Enable RBAC, network policies, and pod security policies
6. **Production Deployment**: Promote applications to stage and prod environments

---

## Estimated Total Time

- **Infrastructure Setup**: 45 minutes
- **ArgoCD Installation**: 15 minutes
- **Git Repository Setup**: 5 minutes
- **Jenkins Deployment**: 20 minutes
- **Container Registry**: 10 minutes
- **Application Deployment**: 20 minutes
- **CI/CD Testing**: 15 minutes
- **Ingress Setup**: 15 minutes

**Total: ~2.5 hours** (first-time setup)

---

## Useful Commands Reference

```bash
# View all resources in a namespace
kubectl get all -n jenkins

# Follow pod logs
kubectl logs -f -n jenkins <pod-name>

# Execute command in pod
kubectl exec -it -n jenkins <pod-name> -- /bin/bash

# Port forward service
kubectl port-forward svc/<service-name> -n <namespace> LOCAL_PORT:REMOTE_PORT

# Delete and recreate application
argocd app delete <app-name>
argocd app create <app-name> ...

# Sync all ArgoCD applications
argocd app sync --l app.kubernetes.io/instance=jenkins

# View Jenkins pod logs
kubectl logs -n jenkins -l app=jenkins --tail=100 -f
```

---

**Good luck with your Jenkins GitOps platform! 🚀**
