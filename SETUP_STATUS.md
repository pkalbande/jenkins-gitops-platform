# Jenkins GitOps Setup Status

## ✅ Completed

### 1. Jenkins Deployment
- ✅ Jenkins deployed via ArgoCD in `jenkins` namespace
- ✅ Jenkins accessible at http://localhost:9090
- ✅ Login credentials: admin/admin123
- ✅ Persistent storage configured (20Gi PVC)
- ✅ Service Account and RBAC configured

### 2. Plugins Installed
- ✅ **Release Plugin** (release:2.12) - For release management
- ✅ **Promoted Builds Plugin** (promoted-builds:876.v99d29788b_36b_) - For build promotions
- ✅ **Job DSL Plugin** (job-dsl:latest) - For programmatic job creation
- ✅ **Pipeline Plugin** (workflow-aggregator:latest) - For pipeline jobs

### 3. Documentation Created
- ✅ [JENKINS_PROMOTION_WORKFLOW.md](JENKINS_PROMOTION_WORKFLOW.md) - Complete promotion workflow guide
- ✅ [LOCAL_SETUP_GUIDE.md](LOCAL_SETUP_GUIDE.md) - Local development setup (updated with job details)
- ✅ [PRODUCTION_AWS_SETUP_GUIDE.md](PRODUCTION_AWS_SETUP_GUIDE.md) - AWS production architecture
- ✅ [PRODUCTION_DEPLOYMENT_STEPS.md](PRODUCTION_DEPLOYMENT_STEPS.md) - Production deployment procedures
- ✅ [QUICK_REFERENCE_GUIDE.md](QUICK_REFERENCE_GUIDE.md) - Operational runbook

### 4. Pipeline Configuration
- ✅ Release Build Jenkinsfile with artifact archiving
- ✅ Promotion Orchestrator Jenkinsfile with promotion logic
- ✅ Job configurations defined in `values-local.yaml` with promotion levels

### 5. Git Repository
- ✅ All changes committed and pushed to master
- ✅ ArgoCD synced to latest commit (6428d5e)

## ⚠️ Manual Steps Required

### Job Creation (Manual UI Steps)

Since JCasC cannot directly execute Job DSL scripts, the jobs need to be created manually **once**. After creation, they will persist in Jenkins home (PVC).

#### Step 1: Create Release Build Job

1. Go to Jenkins: http://localhost:9090
2. Click **"New Item"**
3. Enter name: `release-build-job`
4. Select: **"Pipeline"**
5. Click **"OK"**

6. Configure the job:
   - **Description**: `🚀 Release Build Job - Build code, create versioned release, and archive artifacts using Release Plugin`
   - **Build Triggers**: None (manual)
   - **Build Steps**: Scroll to **"Pipeline"** section
     - **Definition**: Pipeline script from SCM
     - **SCM**: Git
       - **Repository URL**: `https://github.com/pkalbande/jenkins-gitops-platform.git`
       - **Credentials**: Select `github-token`
       - **Branch**: `*/master`
     - **Script Path**: `jenkins/pipelines/release-build/Jenkinsfile`

7. Add Parameters (click "This project is parameterized"):
   - **Choice Parameter**:
     - Name: `APPLICATION`
     - Choices (one per line):
       ```
       app1-node
       app2-python
       ```
     - Description: `Select application to build`
   
   - **String Parameter**:
     - Name: `VERSION`
     - Default Value: `1.0.0`
     - Description: `Version number for the release (e.g., 1.0.0)`
   
   - **Boolean Parameter**:
     - Name: `DEPLOY_TO_DEV`
     - Default: ✓ (checked)
     - Description: `Automatically deploy to dev environment after build`

8. Add Promotions:
   - Click **"Add Promotion"** (should appear if Promoted Builds plugin is installed)
   - If the option is not available, skip this step - promotions can be added after first build

9. Click **"Save"**

#### Step 2: Create Promotion Orchestrator Job

1. Click **"New Item"**
2. Enter name: `promotion-orchestrator-job`
3. Select: **"Pipeline"**
4. Click **"OK"**

5. Configure the job:
   - **Description**: `🎯 Promotion Orchestrator Job - Select and promote previous builds across environments (DEV → QA → STAGE → PROD)`
   - **Build Steps**: Scroll to **"Pipeline"** section
     - **Definition**: Pipeline script from SCM
     - **SCM**: Git
       - **Repository URL**: `https://github.com/pkalbande/jenkins-gitops-platform.git`
       - **Credentials**: Select `github-token`
       - **Branch**: `*/master`
     - **Script Path**: `jenkins/pipelines/promotion-orchestrator/Jenkinsfile`

6. Add Parameters:
   - **Choice Parameter**:
     - Name: `APPLICATION`
     - Choices:
       ```
       app1-node
       app2-python
       ```
   
   - **String Parameter**:
     - Name: `BUILD_NUMBER`
     - Default Value: (leave empty)
     - Description: `Build number from release-build-job to promote`
   
   - **String Parameter**:
     - Name: `VERSION`
     - Default Value: (leave empty)
     - Description: `Version to promote (e.g., 1.0.0)`
   
   - **Choice Parameter**:
     - Name: `PROMOTION_LEVEL`
     - Choices:
       ```
       Deploy-to-DEV
       Deploy-to-QA
       Deploy-to-STAGE
       Deploy-to-PROD
       ```
   
   - **Text Parameter**:
     - Name: `APPROVAL_NOTES`
     - Default Value: (leave empty)
     - Description: `Approval notes and justification`

7. Click **"Save"**

#### Step 3: Configure Promotions for Release Build Job

After creating `release-build-job` and running at least one successful build:

1. Go to **release-build-job** → **Configure**
2. Scroll down to **"Promote builds when..."** section
3. Click **"Add"** to add promotion processes:

**Promotion 1: Deploy-to-DEV**
- Name: `Deploy-to-DEV`
- Icon: ⭐ (Gold Star)
- Criteria: **Manual approval**
  - Add condition: **"Only when manually approved"**
  - Approvers: admin

**Promotion 2: Deploy-to-QA**
- Name: `Deploy-to-QA`
- Icon: ⭐ (Blue Star)
- Criteria:
  - **"Only when manually approved"** - Approvers: admin
  - **"When the following upstream promotions are promoted"** - Select: Deploy-to-DEV

**Promotion 3: Deploy-to-STAGE**
- Name: `Deploy-to-STAGE`
- Icon: ⭐ (Silver Star)
- Criteria:
  - **"Only when manually approved"** - Approvers: admin
  - **"When the following upstream promotions are promoted"** - Select: Deploy-to-QA

**Promotion 4: Deploy-to-PROD**
- Name: `Deploy-to-PROD`
- Icon: ⭐ (Red Star)
- Criteria:
  - **"Only when manually approved"** - Approvers: admin
  - **"When the following upstream promotions are promoted"** - Select: Deploy-to-STAGE

4. Click **"Save"**

## 📋 Quick Verification

After creating the jobs manually, verify the setup:

```bash
# Check Jenkins is running
kubectl get pods -n jenkins

# Access Jenkins
# Port-forward should already be running, if not:
kubectl port-forward svc/jenkins -n jenkins 9090:8080 &

# Open browser
open http://localhost:9090

# Login
Username: admin
Password: admin123
```

**Expected Result**:
- ✓ Two jobs visible: `🚀 release-build-job` and `🎯 promotion-orchestrator-job`
- ✓ Both jobs have green checkmarks (never built) or build history
- ✓ release-build-job shows promotion icons after first successful build

## 🚀 Test the Workflow

After jobs are created, test the complete workflow:

### 1. Create a Release Build

1. Go to **🚀 release-build-job**
2. Click **"Build with Parameters"**
3. Configure:
   - APPLICATION: `app1-node`
   - VERSION: `1.0.0`
   - DEPLOY_TO_DEV: ✓ (checked)
4. Click **"Build"**
5. Wait for build to complete (should see #1 SUCCESS)

### 2. Promote to DEV

**Option A: Direct Promotion (in Release Build Job)**
1. Click on **Build #1**
2. Left menu → Click **"Promotion Status"**
3. Click **⭐ Deploy-to-DEV**
4. Click **"Approve"**

**Option B: Using Promotion Orchestrator**
1. Go to **🎯 promotion-orchestrator-job**
2. Click **"Build with Parameters"**
3. Configure:
   - APPLICATION: `app1-node`
   - BUILD_NUMBER: `1`
   - VERSION: `1.0.0`
   - PROMOTION_LEVEL: `Deploy-to-DEV`
   - APPROVAL_NOTES: `Initial DEV deployment`
4. Click **"Build"**

### 3. Continue Promotions

Repeat the promotion process for:
- Deploy-to-QA (requires Deploy-to-DEV complete)
- Deploy-to-STAGE (requires Deploy-to-QA complete)  
- Deploy-to-PROD (requires Deploy-to-STAGE complete)

## 📚 Reference Documentation

After manual job creation, refer to these guides:

- **[JENKINS_PROMOTION_WORKFLOW.md](JENKINS_PROMOTION_WORKFLOW.md)** - Complete workflow guide with examples
- **[LOCAL_SETUP_GUIDE.md](LOCAL_SETUP_GUIDE.md)** - Phase 8b explains job configuration
- **Troubleshooting** - See "Troubleshooting" section in JENKINS_PROMOTION_WORKFLOW.md

## 🔧 Alternative: Automated Job Creation (Future Enhancement)

To automate job creation in future, consider:

1. **Jenkins Job Builder (JJB)**:
   - Define jobs in YAML
   - Use init container to run `jenkins-jobs update` on startup

2. **Job DSL Seed Job**:
   - Create a seed job manually once
   - Seed job reads DSL scripts from Git and creates other jobs
   - Recommended for large-scale Jenkins setups

3. **Groovy Init Script**:
   - Add init script to `/var/jenkins_home/init.groovy.d/`
   - Script runs on Jenkins startup and creates jobs
   - Requires mounting init scripts via ConfigMap

## 📊 Current Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Local Development Setup                   │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Kind Cluster (kind-2)                                       │
│  ├── jenkins namespace                                       │
│  │   ├── Jenkins Pod (Running)                              │
│  │   │   ├── Plugins: ✓ release, ✓ promoted-builds,       │
│  │   │   │            ✓ job-dsl, ✓ workflow-aggregator     │
│  │   │   └── Jobs: ⚠️  Manual creation required            │
│  │   ├── PVC: 20Gi (local-storage)                         │
│  │   └── Service: NodePort 30000                           │
│  │                                                           │
│  ├── argocd namespace                                       │
│  │   └── ArgoCD (managing Jenkins)                         │
│  │                                                           │
│  └── Git Repository                                         │
│      ├── Jenkinsfiles: ✓ Ready                             │
│      ├── Job configs: ✓ Defined in values-local.yaml       │
│      └── Documentation: ✓ Complete                         │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## ✅ Summary

**What Works:**
- Jenkins deployed and accessible
- All required plugins installed
- Pipeline configurations ready
- Comprehensive documentation available
- Git repository fully configured

**What Needs Manual Setup:**
- Create two Jenkins jobs via UI (one-time setup)
- Configure promotion processes (one-time setup)
- Jobs will persist in PVC after creation

**Next Steps:**
1. Follow "Manual Steps Required" section above
2. Create both jobs via Jenkins UI
3. Configure promotions in release-build-job
4. Test the workflow with a sample build
5. Refer to JENKINS_PROMOTION_WORKFLOW.md for detailed usage

---

**Need Help?**
- Jobs not showing after creation: Check Jenkins logs with `kubectl logs -n jenkins deployment/jenkins`
- Plugins not working: Verify installation with the plugin manager at http://localhost:9090/pluginManager/
- Port-forward not working: Restart with `kubectl port-forward svc/jenkins -n jenkins 9090:8080`

**Resources:**
- Jenkins UI: http://localhost:9090
- Credentials: admin/admin123
- Pipeline Scripts: `jenkins/pipelines/**/Jenkinsfile`
- Job Configs: `jenkins/helm/values-local.yaml`
