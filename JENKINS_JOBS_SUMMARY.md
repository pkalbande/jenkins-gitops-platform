# Jenkins Jobs Summary

## Overview

Jenkins is now configured to be deployed by ArgoCD with two main jobs for GitOps workflow.

---

## Job 1: Release Job

**Name:** `release-job`

**Purpose:** Build, test, and publish application releases

**Location:** `jenkins/pipelines/release-build/Jenkinsfile`

### Parameters:
- **APPLICATION**: Choice (app1-node, app2-python)
- **VERSION**: String (e.g., 1.0.0)
- **DEPLOY_TO_DEV**: Boolean (auto-deploy to dev environment)

### Pipeline Stages:

1. **Checkout** - Clone repository from GitHub
2. **Validate** - Verify application structure and Dockerfile
3. **Build Docker Image** - Build and tag Docker image
4. **Run Tests** - Execute unit tests
5. **Push to Registry** - Push image to localhost:5000 registry
6. **Create Git Tag** - Tag release in Git (e.g., app1-node-1.0.0)
7. **Update Dev Environment** - Update dev/values.yaml with new version
8. **Trigger ArgoCD Sync** - Notify ArgoCD of changes

### Workflow:
```
GitHub → Build → Test → Registry → Git Tag → Update Manifests → ArgoCD Deploy
```

### Usage Example:
```groovy
// Trigger via Jenkins UI or API
build job: 'release-job', parameters: [
    choice(name: 'APPLICATION', value: 'app1-node'),
    string(name: 'VERSION', value: '1.0.0'),
    booleanParam(name: 'DEPLOY_TO_DEV', value: true)
]
```

---

## Job 2: Promotion Orchestrator Job

**Name:** `promotion-orchestrator-job`

**Purpose:** Promote validated releases between environments

**Location:** `jenkins/pipelines/promotion-orchestrator/Jenkinsfile`

### Parameters:
- **SOURCE_ENV**: String (dev, test, stage)
- **TARGET_ENV**: String (test, stage, prod)
- **APPLICATION**: String (app1, app2)
- **VERSION**: String (version to promote)

### Pipeline Stages:

1. **Validate Parameters** - Check environment path and version
2. **Verify Source Deployment** - Confirm version exists in source
3. **Run Tests** - Execute promotion validation tests
4. **Update Target Environment** - Update target/values.yaml
5. **Trigger ArgoCD Sync** - Sync target environment
6. **Verify Deployment** - Confirm successful deployment

### Valid Promotion Paths:
- dev → test
- test → stage
- stage → prod

### Workflow:
```
Dev → Validate → Test → Update Manifests → ArgoCD Deploy → Verify
```

### Usage Example:
```groovy
// Promote app1 version 1.0.0 from dev to test
build job: 'promotion-orchestrator-job', parameters: [
    string(name: 'SOURCE_ENV', value: 'dev'),
    string(name: 'TARGET_ENV', value: 'test'),
    string(name: 'APPLICATION', value: 'app1'),
    string(name: 'VERSION', value: '1.0.0')
]
```

---

## Jenkins Plugins Installed

### Core Plugins:
- **kubernetes**: Kubernetes plugin for dynamic agents
- **workflow-aggregator**: Pipeline suite
- **git**: Git integration
- **configuration-as-code**: JCasC support
- **job-dsl**: Job DSL for job creation

### Build & Release Plugins:
- **pipeline-stage-view**: Visual pipeline view
- **pipeline-graph-view**: Pipeline graph visualization
- **build-pipeline-plugin**: Build pipeline view
- **delivery-pipeline-plugin**: Delivery pipeline
- **promoted-builds**: Build promotion support

### SCM Plugins:
- **github**: GitHub integration
- **github-branch-source**: GitHub branch discovery

### Docker Plugins:
- **docker-workflow**: Docker pipeline steps
- **docker-plugin**: Docker cloud support

### Utility Plugins:
- **credentials**: Credentials management
- **credentials-binding**: Credential binding
- **timestamper**: Build timestamps
- **ws-cleanup**: Workspace cleanup
- **ansicolor**: ANSI color output
- **build-timeout**: Build timeout support
- **email-ext**: Extended email notifications

---

## Configuration as Code (JCasC)

Jenkins is fully configured via JCasC:

### Security Configuration:
```yaml
jenkins:
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: admin
          password: admin123
  authorizationStrategy:
    loggedInUsersCanDoAnything:
      allowAnonymousRead: false
```

### Kubernetes Cloud:
```yaml
clouds:
  - kubernetes:
      name: kubernetes
      serverUrl: https://kubernetes.default
      namespace: jenkins
      jenkinsUrl: http://jenkins:8080
      containerCapStr: 5
      templates:
        - name: jnlp-agent
          label: jnlp-agent
```

### Credentials:
- **github-token**: GitHub Personal Access Token
- **docker-registry**: Docker registry credentials
- **argocd-token**: ArgoCD API token

### Jobs:
Both jobs are automatically created via Job DSL in JCasC configuration.

---

## GitOps Workflow

### Complete CI/CD Flow:

```
┌─────────────────────────────────────────────────────────────┐
│  Developer pushes code to GitHub                            │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│  1. RELEASE JOB (Jenkins)                                   │
│     - Build Docker image                                     │
│     - Run tests                                              │
│     - Push to registry (localhost:5000)                      │
│     - Create Git tag                                         │
│     - Update environments/dev/values.yaml                    │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│  2. ARGOCD AUTO-SYNC                                        │
│     - Detects manifest changes                              │
│     - Deploys to dev namespace                              │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│  3. VALIDATION (Manual/Automated)                           │
│     - Test application in dev                               │
│     - Verify functionality                                  │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│  4. PROMOTION ORCHESTRATOR JOB (Jenkins)                    │
│     - Validate source deployment                            │
│     - Run promotion tests                                   │
│     - Update environments/test/values.yaml                  │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│  5. ARGOCD AUTO-SYNC                                        │
│     - Deploys to test namespace                             │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│  6. REPEAT for stage → prod                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Environment Structure

```
environments/
├── dev/
│   ├── app1-values.yaml    # Dev configuration for app1
│   └── app2-values.yaml    # Dev configuration for app2
├── test/
│   ├── app1-values.yaml    # Test configuration for app1
│   └── app2-values.yaml    # Test configuration for app2
├── stage/
│   ├── app1-values.yaml    # Stage configuration for app1
│   └── app2-values.yaml    # Stage configuration for app2
└── prod/
    ├── app1-values.yaml    # Production configuration for app1
    └── app2-values.yaml    # Production configuration for app2
```

---

## Quick Commands

### Build a Release:
```bash
# Via Jenkins CLI (if installed)
java -jar jenkins-cli.jar -s http://localhost:9090 build release-job \
  -p APPLICATION=app1-node \
  -p VERSION=1.0.0 \
  -p DEPLOY_TO_DEV=true
```

### Promote to Test:
```bash
java -jar jenkins-cli.jar -s http://localhost:9090 build promotion-orchestrator-job \
  -p SOURCE_ENV=dev \
  -p TARGET_ENV=test \
  -p APPLICATION=app1 \
  -p VERSION=1.0.0
```

### Check Build Status:
```bash
# View ArgoCD app status
argocd app get app1-dev
argocd app get app1-test

# Check pods
kubectl get pods -n dev
kubectl get pods -n test
```

---

## Benefits of This Setup

✅ **GitOps Principles**: All changes tracked in Git  
✅ **Automated Deployment**: ArgoCD handles deployments  
✅ **Controlled Promotions**: Structured promotion path  
✅ **Version Control**: Every release tagged in Git  
✅ **Rollback Support**: Easy rollback via Git  
✅ **Audit Trail**: Complete history of deployments  
✅ **Self-Service**: Developers can deploy/promote  
✅ **Infrastructure as Code**: Everything in Git  

---

**Jenkins + ArgoCD = Perfect GitOps! 🚀**
