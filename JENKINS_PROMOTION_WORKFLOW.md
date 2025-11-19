# Jenkins Promotion Workflow Guide

## Overview

This guide documents the Jenkins promotion workflow using the **Release Plugin** and **Promoted Builds Plugin** to manage application deployments across multiple environments (DEV → QA → STAGE → PROD).

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Jenkins Promotion Pipeline                    │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────────┐         ┌──────────────────────────────┐
│                      │         │                              │
│  🚀 Release Build    │────────▶│  🎯 Promotion Orchestrator   │
│       Job            │         │         Job                  │
│                      │         │                              │
└──────────────────────┘         └──────────────────────────────┘
         │                                    │
         │                                    │
         ▼                                    ▼
┌─────────────────────┐          ┌────────────────────────────┐
│  Build Artifacts    │          │  Trigger Promotions        │
│  - Docker Image     │          │  - DEV → QA → STAGE → PROD │
│  - Metadata         │          │  - Manual Approval         │
│  - Release Notes    │          │  - Automated Deployment    │
└─────────────────────┘          └────────────────────────────┘
         │                                    │
         │                                    │
         ▼                                    ▼
┌──────────────────────────────────────────────────────────────┐
│               Promotion Levels (Promoted Builds)             │
├──────────────────────────────────────────────────────────────┤
│  ⭐ Deploy-to-DEV    │  Manual Approval (admin)              │
│  ⭐ Deploy-to-QA     │  Requires: Deploy-to-DEV              │
│  ⭐ Deploy-to-STAGE  │  Requires: Deploy-to-QA               │
│  ⭐ Deploy-to-PROD   │  Requires: Deploy-to-STAGE            │
└──────────────────────────────────────────────────────────────┘
```

## Jobs Configuration

### Job 1: 🚀 Release Build Job

**Purpose**: Build code, create versioned Docker images, and archive release artifacts.

**Features**:
- Builds Docker images for selected applications (app1-node, app2-python)
- Creates versioned releases with semantic versioning
- Archives build artifacts (release-metadata.json, Dockerfile, RELEASE_NOTES.txt)
- Automatically deploys to DEV environment (optional)
- **Integrated with Promoted Builds Plugin** for 4-tier promotion workflow

**Parameters**:
```yaml
APPLICATION: [app1-node, app2-python]
VERSION: "1.0.0"  # Semantic version
DEPLOY_TO_DEV: true  # Auto-deploy to DEV
```

**Promotion Levels** (configured via Promoted Builds Plugin):

1. **⭐ Deploy-to-DEV** (Gold Star)
   - Condition: Manual approval by admin
   - Action: Deploy to DEV environment
   - Prerequisite: None (first promotion)

2. **⭐ Deploy-to-QA** (Blue Star)
   - Condition: Manual approval by admin
   - Action: Deploy to QA environment
   - Prerequisite: Deploy-to-DEV must be completed

3. **⭐ Deploy-to-STAGE** (Silver Star)
   - Condition: Manual approval by admin
   - Action: Deploy to STAGE environment
   - Prerequisite: Deploy-to-QA must be completed

4. **⭐ Deploy-to-PROD** (Red Star)
   - Condition: Manual approval by admin
   - Action: Deploy to PROD environment
   - Prerequisite: Deploy-to-STAGE must be completed

**Pipeline Location**: `jenkins/pipelines/release-build/Jenkinsfile`

**Build Retention**: Keeps last 10 builds with artifacts

---

### Job 2: 🎯 Promotion Orchestrator Job

**Purpose**: Trigger and manage promotions of builds across environments using the Promoted Builds Plugin API.

**Features**:
- Lists all available builds from Release Build Job
- Shows promotion status for each build (which promotions are complete)
- Validates build exists and completed successfully
- Checks promotion prerequisites (ensures proper promotion order)
- Triggers promotions via Promoted Builds Plugin API
- Updates environment configurations in Git
- Provides detailed audit trail with approval notes

**Parameters**:
```yaml
APPLICATION: [app1-node, app2-python]
BUILD_NUMBER: "5"  # From release-build-job
VERSION: "1.0.0"  # Version to promote
PROMOTION_LEVEL: [Deploy-to-DEV, Deploy-to-QA, Deploy-to-STAGE, Deploy-to-PROD]
APPROVAL_NOTES: "Text field for approval justification"
```

**Workflow Stages**:

1. **List Available Builds**
   - Queries release-build-job for successful builds
   - Displays build number, application, version, and promotion status
   - Example: `#5 - app1-node:1.0.0 ✓ [Deploy-to-DEV, Deploy-to-QA]`

2. **Validate Parameters**
   - Verifies build number is provided
   - Validates version matches
   - Maps promotion level to target environment

3. **Verify Build Exists**
   - Confirms build exists in release-build-job
   - Checks build completed successfully
   - Validates version matches the specified build

4. **Check Promotion Prerequisites**
   - Verifies required promotions are complete
   - Example: Deploy-to-STAGE requires Deploy-to-QA
   - Prevents out-of-order promotions

5. **Trigger Promotion**
   - Calls Promoted Builds Plugin API
   - Manually approves the promotion
   - Records approval notes and timestamp

6. **Update Target Environment**
   - Updates environment configuration in Git
   - Creates/updates values file with new version
   - Commits with detailed promotion information

7. **Trigger ArgoCD Sync**
   - ArgoCD auto-syncs the configuration changes
   - Deploys new version to target environment

8. **Verify Deployment**
   - Checks pods are running in target environment
   - Provides deployment status

**Pipeline Location**: `jenkins/pipelines/promotion-orchestrator/Jenkinsfile`

---

## Promotion Workflow

### Complete Promotion Path

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│             │      │             │      │             │      │             │
│     DEV     │─────▶│     QA      │─────▶│    STAGE    │─────▶│    PROD     │
│             │      │             │      │             │      │             │
└─────────────┘      └─────────────┘      └─────────────┘      └─────────────┘
   Deploy-to-DEV      Deploy-to-QA       Deploy-to-STAGE     Deploy-to-PROD
   (Gold Star)        (Blue Star)        (Silver Star)       (Red Star)
   
   Manual Approval    Requires DEV       Requires QA         Requires STAGE
```

### Step-by-Step Promotion Process

#### Step 1: Create Release Build

1. Go to Jenkins: http://localhost:9090
2. Click on **"🚀 Release Build Job"**
3. Click **"Build with Parameters"**
4. Configure:
   - APPLICATION: `app1-node`
   - VERSION: `1.0.0`
   - DEPLOY_TO_DEV: `true` (checked)
5. Click **"Build"**
6. Wait for build to complete successfully
7. Note the **Build Number** (e.g., #5)

**What happens:**
- Code is checked out from Git
- Docker image is built: `localhost:5000/app1-node:1.0.0`
- Image is pushed to registry
- Artifacts are archived (metadata, Dockerfile, release notes)
- If DEPLOY_TO_DEV is true, automatically deploys to dev environment

#### Step 2: Promote to DEV (First Promotion)

**Option A: Manual Promotion in Release Build Job**
1. Go to **"🚀 Release Build Job"**
2. Click on **Build #5** (your build number)
3. In the left menu, click **"Promotion Status"**
4. Click **"⭐ Deploy-to-DEV"**
5. Click **"Approve"**

**Option B: Using Promotion Orchestrator**
1. Go to **"🎯 Promotion Orchestrator Job"**
2. Click **"Build with Parameters"**
3. Configure:
   - APPLICATION: `app1-node`
   - BUILD_NUMBER: `5`
   - VERSION: `1.0.0`
   - PROMOTION_LEVEL: `Deploy-to-DEV`
   - APPROVAL_NOTES: `Initial deployment to DEV environment`
4. Click **"Build"**

**What happens:**
- Build #5 is marked as promoted to DEV
- Environment configuration is updated in Git
- ArgoCD syncs and deploys to dev namespace
- Build history shows: `#5 - app1-node:1.0.0 ✓ [Deploy-to-DEV]`

#### Step 3: Promote to QA (Second Promotion)

1. Go to **"🎯 Promotion Orchestrator Job"**
2. Click **"Build with Parameters"**
3. Review the **"List Available Builds"** output (shown at start of build)
   - Verify your build shows: `#5 - app1-node:1.0.0 ✓ [Deploy-to-DEV]`
4. Configure:
   - APPLICATION: `app1-node`
   - BUILD_NUMBER: `5`
   - VERSION: `1.0.0`
   - PROMOTION_LEVEL: `Deploy-to-QA`
   - APPROVAL_NOTES: `QA testing approved, ready for staging`
5. Click **"Build"**

**What happens:**
- Job validates Deploy-to-DEV is complete (prerequisite check)
- Build #5 is promoted to QA
- Environment configuration updated: `environments/qa/app1-node-values.yaml`
- ArgoCD deploys version 1.0.0 to qa namespace
- Build history shows: `#5 - app1-node:1.0.0 ✓ [Deploy-to-DEV, Deploy-to-QA]`

#### Step 4: Promote to STAGE (Third Promotion)

1. Run **"🎯 Promotion Orchestrator Job"** with:
   - BUILD_NUMBER: `5`
   - PROMOTION_LEVEL: `Deploy-to-STAGE`
   - APPROVAL_NOTES: `Staging validation completed`

**Prerequisites validated:**
- ✓ Deploy-to-DEV completed
- ✓ Deploy-to-QA completed

#### Step 5: Promote to PROD (Final Promotion)

1. Run **"🎯 Promotion Orchestrator Job"** with:
   - BUILD_NUMBER: `5`
   - PROMOTION_LEVEL: `Deploy-to-PROD`
   - APPROVAL_NOTES: `Production deployment approved by PM and Tech Lead`

**Prerequisites validated:**
- ✓ Deploy-to-DEV completed
- ✓ Deploy-to-QA completed
- ✓ Deploy-to-STAGE completed

**Final result:**
- Build #5 fully promoted: `#5 - app1-node:1.0.0 ✓ [Deploy-to-DEV, Deploy-to-QA, Deploy-to-STAGE, Deploy-to-PROD]`
- Same build artifact deployed across all environments
- Complete audit trail in Git commits and Jenkins build history

---

## Plugins Installed

### 1. Release Plugin (release:2.12)
**Purpose**: Manages versioned releases and release metadata

**Features Used**:
- Build retention with artifacts
- Release versioning
- Artifact fingerprinting

**Documentation**: https://plugins.jenkins.io/release/

### 2. Promoted Builds Plugin (promoted-builds:876.v99d29788b_36b_)
**Purpose**: Manages build promotions through defined processes

**Features Used**:
- Manual approval conditions
- Promotion process chaining (prerequisites)
- Promotion status tracking
- Build promotion history
- Icon-based promotion levels (gold, blue, silver, red stars)

**Documentation**: https://plugins.jenkins.io/promoted-builds/

**Installation Method**: Both plugins are installed via init container in Jenkins deployment:
```yaml
initContainers:
  - name: install-additional-plugins
    image: jenkins/jenkins:lts-jdk17
    command:
      - jenkins-plugin-cli
      - --plugins
      - release:2.12
      - promoted-builds:876.v99d29788b_36b_
```

---

## Jenkins Configuration as Code (JCasC)

The jobs are configured declaratively in `jenkins/helm/values-local.yaml`:

```yaml
jobs:
  - script: >
      pipelineJob('release-build-job') {
        properties {
          promotions {
            promotion {
              name('Deploy-to-DEV')
              icon('star-gold')
              conditions {
                manual('admin')
              }
            }
            promotion {
              name('Deploy-to-QA')
              icon('star-blue')
              conditions {
                manual('admin')
                upstream('Deploy-to-DEV')
              }
            }
            # ... more promotions
          }
        }
      }
```

**Benefits**:
- Version-controlled configuration
- Repeatable deployments
- No manual Jenkins UI configuration required
- GitOps-friendly

---

## Git Repository Structure

```
jenkins-gitops-argo-k8s/
├── apps/
│   ├── app1-node/
│   │   └── Dockerfile
│   └── app2-python/
│       └── Dockerfile
├── environments/
│   ├── dev/
│   │   ├── app1-node-values.yaml
│   │   └── app2-python-values.yaml
│   ├── qa/
│   │   ├── app1-node-values.yaml
│   │   └── app2-python-values.yaml
│   ├── stage/
│   │   ├── app1-node-values.yaml
│   │   └── app2-python-values.yaml
│   └── prod/
│       ├── app1-node-values.yaml
│       └── app2-python-values.yaml
├── jenkins/
│   ├── helm/
│   │   ├── values-local.yaml          # Jenkins configuration with jobs
│   │   └── templates/
│   │       └── deployment.yaml         # Init container for plugins
│   └── pipelines/
│       ├── release-build/
│       │   └── Jenkinsfile            # Build and create release
│       └── promotion-orchestrator/
│           └── Jenkinsfile            # Trigger promotions
└── argocd/
    └── applications/
        └── jenkins.yaml                # ArgoCD application
```

---

## Environment Values Format

Each environment has values files that specify the Docker image version:

**Example: `environments/dev/app1-node-values.yaml`**
```yaml
image:
  repository: localhost:5000/app1-node
  tag: 1.0.0
  pullPolicy: Always

# Promoted from build #5
# Promotion Level: Deploy-to-DEV
# Date: 2025-01-19T14:30:00Z
```

When a promotion occurs, the Promotion Orchestrator Job:
1. Updates the `tag` field in the target environment
2. Adds promotion metadata as comments
3. Commits to Git with detailed message
4. ArgoCD auto-syncs the change
5. Kubernetes deploys the new version

---

## Viewing Promotion Status

### In Jenkins UI

1. **Release Build Job View**:
   - Go to http://localhost:9090/job/release-build-job/
   - Each build shows promotion icons (⭐) in the build list
   - Gold stars indicate completed promotions

2. **Individual Build View**:
   - Click on a specific build (e.g., #5)
   - Left menu shows **"Promotion Status"**
   - Click to see all promotions and their status
   - Options to approve pending promotions

3. **Build History with Promotions**:
   ```
   #8  ✓✓✓✓  app1-node:1.2.0  [DEV, QA, STAGE, PROD]
   #7  ✓✓✓   app2-python:1.1.0  [DEV, QA, STAGE]
   #6  ✓✓    app1-node:1.1.0  [DEV, QA]
   #5  ✓     app1-node:1.0.0  [DEV]
   ```

### In Promotion Orchestrator Output

The "List Available Builds" stage shows:
```
📦 Recent successful release builds:

  #8 - app1-node:1.2.0 ✓ [Deploy-to-DEV, Deploy-to-QA, Deploy-to-STAGE, Deploy-to-PROD]
  #7 - app2-python:1.1.0 ✓ [Deploy-to-DEV, Deploy-to-QA, Deploy-to-STAGE]
  #6 - app1-node:1.1.0 ✓ [Deploy-to-DEV, Deploy-to-QA]
  #5 - app1-node:1.0.0 ✓ [Deploy-to-DEV]
```

### In Git Commits

Each promotion creates a detailed commit:
```
🚀 Promote app1-node:1.0.0 to qa [Build #5]

Promotion: Deploy-to-QA
Source Build: #5
Approval Notes: QA testing approved, ready for staging
```

---

## Best Practices

### 1. Version Management
- Use semantic versioning (MAJOR.MINOR.PATCH)
- Increment version for each release build
- Keep version consistent across promotions
- Example progression:
  - Build #5: app1-node:1.0.0
  - Build #6: app1-node:1.0.1 (patch)
  - Build #7: app1-node:1.1.0 (minor)

### 2. Promotion Order
- **Always** follow the promotion path: DEV → QA → STAGE → PROD
- Never skip environments (enforced by prerequisites)
- Use approval notes to document decisions
- Get sign-off before production promotions

### 3. Build Artifacts
- Archive important artifacts for troubleshooting
- Keep artifacts for promoted builds
- Document build metadata (commit SHA, timestamp, author)

### 4. Rollback Strategy
- To rollback, promote a previous successful build
- Example: If build #8 fails in PROD, promote build #7
- All builds are immutable and can be re-promoted

### 5. Testing at Each Stage
- DEV: Developer testing, unit tests
- QA: QA team validation, integration tests
- STAGE: Production-like environment, performance tests
- PROD: Final deployment after all approvals

### 6. Approval Notes
Always include meaningful approval notes:
```
✓ Good: "QA testing completed. All test cases passed. Approved by QA Lead."
✓ Good: "Staging validation successful. Performance benchmarks met. Ready for PROD."
✗ Poor: "approved"
✗ Poor: "ok"
```

---

## Troubleshooting

### Build Not Showing in Promotion List

**Problem**: Build completed but doesn't appear in promotion list

**Solution**:
1. Check build status: `Status: Success` required
2. Verify artifacts were archived
3. Wait 30 seconds for Jenkins to update cache
4. Refresh promotion orchestrator job page

### Promotion Prerequisites Not Met

**Problem**: Error: "Prerequisite promotion not met: Deploy-to-QA"

**Solution**:
1. Check promotion status in release-build-job
2. Complete missing promotions in order
3. Cannot skip promotion levels

### ArgoCD Not Syncing

**Problem**: Promotion succeeded but app not deployed

**Solution**:
```bash
# Check ArgoCD application status
argocd app get jenkins

# Manual sync if needed
argocd app sync jenkins

# Check if environment namespace exists
kubectl get namespace dev qa stage prod
```

### Plugin Not Working

**Problem**: Promotion features not available

**Solution**:
```bash
# Verify plugins are installed
kubectl exec -n jenkins deployment/jenkins -- jenkins-plugin-cli --list

# Check for: release:2.12 and promoted-builds:876.v99d29788b_36b_

# If missing, restart Jenkins
kubectl rollout restart deployment jenkins -n jenkins
```

---

## Accessing Jenkins

**Local Access**:
```bash
# Start port-forward
kubectl port-forward svc/jenkins -n jenkins 9090:8080

# Open browser
http://localhost:9090

# Login credentials
Username: admin
Password: admin123
```

**Jobs Available**:
- 🚀 Release Build Job - Build and create releases
- 🎯 Promotion Orchestrator Job - Promote builds through environments

---

## Next Steps

1. **Create Your First Release**:
   - Build app1-node version 1.0.0
   - Promote through DEV → QA → STAGE → PROD
   - Document the process

2. **Customize Promotion Conditions**:
   - Add automated tests as promotion criteria
   - Configure notification on promotions
   - Set up approval groups

3. **Integrate with ArgoCD**:
   - Create ArgoCD applications for each environment
   - Configure auto-sync policies
   - Set up health checks

4. **Production Deployment**:
   - Review PRODUCTION_AWS_SETUP_GUIDE.md
   - Implement HA Jenkins on AWS EKS
   - Configure production promotion policies

---

## References

- [LOCAL_SETUP_GUIDE.md](LOCAL_SETUP_GUIDE.md) - Phase 8b: Jenkins Job Configuration
- [PRODUCTION_AWS_SETUP_GUIDE.md](PRODUCTION_AWS_SETUP_GUIDE.md) - AWS production architecture
- [Release Plugin Documentation](https://plugins.jenkins.io/release/)
- [Promoted Builds Plugin Documentation](https://plugins.jenkins.io/promoted-builds/)
- [Jenkins Configuration as Code](https://github.com/jenkinsci/configuration-as-code-plugin)

---

## Summary

This promotion workflow provides:

✅ **Immutable Builds**: Same artifact (Docker image) promoted across all environments  
✅ **Controlled Promotion**: Manual approval gates with prerequisite checking  
✅ **Audit Trail**: Complete history in Git commits and Jenkins build records  
✅ **GitOps Integration**: Environment configs in Git, managed by ArgoCD  
✅ **Plugin-Based**: Uses Release Plugin and Promoted Builds Plugin features  
✅ **Declarative Configuration**: Jobs defined in JCasC (version-controlled)  
✅ **Rollback Support**: Can re-promote any previous successful build  
✅ **Multi-Application**: Supports multiple applications (app1-node, app2-python)  

The workflow ensures that only validated, approved builds reach production while maintaining complete traceability and the ability to rollback if needed.
