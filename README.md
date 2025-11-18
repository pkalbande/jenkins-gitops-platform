# Jenkins GitOps Platform with ArgoCD on Kubernetes

A comprehensive, production-ready GitOps platform leveraging Jenkins for CI, ArgoCD for CD, and Kubernetes (EKS) for orchestration. This project demonstrates enterprise-grade practices for building, deploying, and managing applications across multiple environments using a GitOps methodology.

## 🏗️ Architecture Overview

This platform implements a complete GitOps workflow:

- **Jenkins**: Continuous Integration (CI) for building and testing applications
- **ArgoCD**: Continuous Deployment (CD) using GitOps principles
- **Amazon EKS**: Managed Kubernetes cluster
- **Amazon EFS**: Persistent storage for Jenkins
- **AWS ALB**: Application Load Balancer for ingress
- **Helm**: Package management for Kubernetes applications
- **Kustomize**: Environment-specific configurations

## 📁 Project Structure

```
jenkins-gitops-argo-k8s/
├── README.md
│
├── argocd/                          # ArgoCD Application configurations
│   ├── app-of-apps.yaml             # App of Apps pattern for ArgoCD
│   ├── jenkins-app.yaml             # Jenkins ArgoCD Application
│   └── values/
│       └── jenkins-values.yaml      # Jenkins Helm values for ArgoCD
│
├── infra/                           # Infrastructure as Code
│   ├── efs/                         # Elastic File System configuration
│   │   ├── efs-provisioner.yaml
│   │   ├── storage-class.yaml
│   │   └── pvc.yaml
│   ├── eks/                         # EKS cluster configuration
│   │   ├── cluster-config.yaml
│   │   └── node-group.yaml
│   └── ingress/                     # Ingress & TLS configuration
│       ├── alb-ingress.yaml
│       └── certificate.yaml
│
├── jenkins/                         # Jenkins configurations
│   ├── helm/                        # Jenkins Helm chart
│   │   ├── Chart.yaml
│   │   ├── templates/
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   ├── ingress.yaml
│   │   │   └── configmap-jcasc.yaml
│   │   └── values.yaml
│   │
│   ├── jcasc/                       # Jenkins Configuration as Code
│   │   ├── jenkins.yaml             # Main Jenkins configuration
│   │   ├── credentials.yaml         # Credentials configuration
│   │   ├── plugins.yaml             # Plugin list
│   │   ├── jobs/
│   │   │   ├── release-job.yaml     # Release pipeline jobs
│   │   │   ├── promote-job.yaml     # Promotion pipeline jobs
│   │   │   └── folder-structure.yaml
│   │   └── casc-config.yaml         # Additional JCasC config
│   │
│   └── pipelines/                   # Pipeline definitions
│       ├── release-build/
│       │   ├── Jenkinsfile          # Build & release pipeline
│       │   └── build.sh
│       ├── promotion-dev/
│       │   ├── Jenkinsfile
│       │   └── validate-release.sh
│       ├── promotion-test/
│       │   └── Jenkinsfile
│       ├── promotion-stage/
│       │   └── Jenkinsfile
│       └── promotion-prod/
│           └── Jenkinsfile
│
├── apps/                            # Sample applications
│   ├── app1-node/                   # Node.js application
│   │   ├── Dockerfile
│   │   ├── src/
│   │   │   └── index.js
│   │   └── package.json
│   │
│   └── app2-python/                 # Python Flask application
│       ├── Dockerfile
│       ├── app.py
│       └── requirements.txt
│
├── helm-charts/                     # Application Helm charts
│   ├── app1/
│   │   ├── Chart.yaml
│   │   ├── templates/
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   └── ingress.yaml
│   │   └── values.yaml
│   │
│   └── app2/
│       ├── Chart.yaml
│       ├── templates/
│       │   ├── deployment.yaml
│       │   ├── service.yaml
│       │   └── ingress.yaml
│       └── values.yaml
│
└── environments/                    # Environment-specific configurations
    ├── dev/
    │   ├── app1-values.yaml
    │   ├── app2-values.yaml
    │   └── kustomization.yaml
    ├── test/
    │   ├── app1-values.yaml
    │   ├── app2-values.yaml
    │   └── kustomization.yaml
    ├── stage/
    │   ├── app1-values.yaml
    │   ├── app2-values.yaml
    │   └── kustomization.yaml
    └── prod/
        ├── app1-values.yaml
        ├── app2-values.yaml
        └── kustomization.yaml
```

## 🚀 Getting Started

### Prerequisites

- AWS Account with appropriate permissions
- AWS CLI configured
- `kubectl` installed
- `helm` installed
- `eksctl` installed
- `argocd` CLI installed
- Docker installed

### 1. Deploy EKS Cluster

```bash
# Create EKS cluster
eksctl create cluster -f infra/eks/cluster-config.yaml

# Add additional node group
eksctl create nodegroup -f infra/eks/node-group.yaml

# Verify cluster
kubectl get nodes
```

### 2. Setup EFS Storage

```bash
# Update EFS ID in infra/efs/efs-provisioner.yaml
# Deploy EFS provisioner
kubectl apply -f infra/efs/efs-provisioner.yaml
kubectl apply -f infra/efs/storage-class.yaml
kubectl apply -f infra/efs/pvc.yaml
```

### 3. Install ArgoCD

```bash
# Create ArgoCD namespace
kubectl create namespace argocd

# Install ArgoCD
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for ArgoCD to be ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=argocd-server -n argocd --timeout=300s

# Get admin password
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d

# Port-forward to access ArgoCD UI
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

### 4. Deploy Jenkins using ArgoCD

```bash
# Update repository URL in argocd/app-of-apps.yaml and argocd/jenkins-app.yaml
# Apply ArgoCD applications
kubectl apply -f argocd/jenkins-app.yaml

# Monitor deployment
argocd app get jenkins
```

### 5. Access Jenkins

```bash
# Get Jenkins initial admin password
kubectl exec -n jenkins $(kubectl get pods -n jenkins -l app=jenkins -o jsonpath='{.items[0].metadata.name}') -- cat /var/jenkins_home/secrets/initialAdminPassword

# Port-forward to access Jenkins
kubectl port-forward svc/jenkins -n jenkins 8080:8080
```

## 🔄 CI/CD Workflow

### Release Pipeline

1. **Build**: Jenkins builds Docker image from source code
2. **Test**: Runs automated tests
3. **Push**: Pushes image to container registry
4. **Tag**: Tags the release with version

```bash
# Trigger release pipeline
# In Jenkins UI: Release/app1-release
# Parameters:
#   - APP_NAME: app1-node
#   - GIT_TAG: v1.0.0
```

### Promotion Pipeline

1. **Validate**: Validates the release artifacts
2. **Update Manifest**: Updates environment-specific values
3. **Commit**: Commits changes to Git
4. **Sync**: ArgoCD automatically syncs changes
5. **Verify**: Validates deployment health

```bash
# Promote to DEV
# In Jenkins UI: Promotion/DEV/promote-to-dev
# Parameters:
#   - APP_NAME: app1-node
#   - VERSION: 1.0.0

# Promote to PROD (requires approval)
# In Jenkins UI: Promotion/PROD/promote-to-prod
# Parameters:
#   - APP_NAME: app1-node
#   - VERSION: 1.0.0
#   - REQUIRE_APPROVAL: true
```

## 🔐 Security Configuration

### Required Secrets

Create the following secrets before deploying:

```bash
# GitHub Token
kubectl create secret generic github-token \
  --from-literal=token=<YOUR_GITHUB_TOKEN> \
  -n jenkins

# Docker Registry Credentials
kubectl create secret docker-registry docker-registry \
  --docker-server=your-registry.azurecr.io \
  --docker-username=<USERNAME> \
  --docker-password=<PASSWORD> \
  -n jenkins

# ArgoCD Token
kubectl create secret generic argocd-token \
  --from-literal=token=<ARGOCD_TOKEN> \
  -n jenkins

# AWS Credentials
kubectl create secret generic aws-credentials \
  --from-literal=access-key-id=<AWS_ACCESS_KEY> \
  --from-literal=secret-access-key=<AWS_SECRET_KEY> \
  -n jenkins
```

## 📊 Monitoring & Observability

### Jenkins Metrics

Jenkins exposes Prometheus metrics at `/prometheus`

### ArgoCD Metrics

ArgoCD provides metrics for application sync status and health

### Access Logs

```bash
# Jenkins logs
kubectl logs -n jenkins -l app=jenkins -f

# ArgoCD logs
kubectl logs -n argocd -l app.kubernetes.io/name=argocd-server -f
```

## 🛠️ Customization

### Update Docker Registry

Replace `your-registry.azurecr.io` in the following files:
- `helm-charts/*/values.yaml`
- `environments/*/app*-values.yaml`
- `jenkins/pipelines/*/Jenkinsfile`

### Update Domain Names

Replace `yourdomain.com` in:
- `argocd/values/jenkins-values.yaml`
- `infra/ingress/alb-ingress.yaml`
- `helm-charts/*/values.yaml`
- `environments/*/app*-values.yaml`

### Update AWS Certificate ARN

Replace certificate ARN in:
- `infra/ingress/alb-ingress.yaml`
- `environments/stage/*-values.yaml`
- `environments/prod/*-values.yaml`

## 📚 Key Features

✅ **GitOps-driven deployments** using ArgoCD  
✅ **Multi-environment support** (dev, test, stage, prod)  
✅ **Automated CI/CD pipelines** with Jenkins  
✅ **Configuration as Code** for Jenkins (JCasC)  
✅ **Helm charts** for application packaging  
✅ **Kustomize overlays** for environment-specific configs  
✅ **Auto-scaling** support for production workloads  
✅ **Health checks** and readiness probes  
✅ **AWS ALB integration** for ingress  
✅ **TLS/SSL support** with cert-manager  
✅ **Persistent storage** with Amazon EFS  
✅ **RBAC** and security best practices  

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📝 License

MIT License - see LICENSE file for details

## 🆘 Troubleshooting

### Jenkins Pod Not Starting

```bash
# Check pod status
kubectl describe pod -n jenkins -l app=jenkins

# Check PVC status
kubectl get pvc -n jenkins
```

### ArgoCD Sync Issues

```bash
# Check application status
argocd app get <app-name>

# Force sync
argocd app sync <app-name> --force
```

### EFS Mount Issues

```bash
# Check EFS provisioner logs
kubectl logs -n kube-system -l app=efs-provisioner
```

## 📧 Support

For issues and questions, please open an issue in the GitHub repository.

---

**Built with ❤️ for the DevOps Community**
