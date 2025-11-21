# Multibranch Pipeline Example

This directory contains a comprehensive multibranch pipeline example for Jenkins that automatically discovers and builds branches from your repository.

## Overview

The multibranch pipeline automatically:
- Discovers branches matching specified criteria
- Creates a separate job for each branch
- Removes jobs for deleted branches
- Builds on push events and pull requests
- Applies different behaviors based on branch type

## Branch Behavior

### Feature/Bugfix Branches (`feature/*`, `bugfix/*`)
- ✅ Build & Test
- ✅ Build Docker Image (local, not pushed)
- ✅ Create PR Comments
- ❌ No deployment

### Develop Branch (`develop`)
- ✅ Build & Test
- ✅ Build & Push Docker Image
- ✅ Auto-deploy to DEV environment

### Main/Master Branch (`main`, `master`)
- ✅ Full CI/CD Pipeline
- ✅ Build & Push Docker Image
- ✅ Manual approval for deployments

## Files

- `Jenkinsfile` - The main pipeline script that runs for each branch
- `../dsl/multibranch-pipeline.groovy` - DSL configuration to create the multibranch job
- `../jcasc/jobs/multibranch-pipeline.yaml` - JCasC configuration for the multibranch job

## Setup

### Option 1: Using Jenkins DSL

1. Add the DSL script to your seed job or DSL configuration:
   ```groovy
   // In jenkins/dsl/jobs.groovy or seed job
   evaluate(new File("${WORKSPACE}/jenkins/dsl/multibranch-pipeline.groovy"))
   ```

2. Run the seed job to create the multibranch pipeline

### Option 2: Using JCasC

1. Add the JCasC configuration to your Jenkins configuration:
   ```yaml
   # In jenkins/jcasc/jobs/multibranch-pipeline.yaml
   ```

2. Restart Jenkins or reload configuration

### Option 3: Manual Setup

1. In Jenkins UI, click "New Item"
2. Enter name: `multibranch-pipeline-example`
3. Select "Multibranch Pipeline"
4. Configure:
   - **Branch Sources**: GitHub
     - Repository: `pkalbande/jenkins-gitops-platform`
     - Credentials: `github-token`
     - Includes: `*`
   - **Build Configuration**: 
     - Script Path: `jenkins/pipelines/multibranch/Jenkinsfile`
   - **Scan Multibranch Pipeline Triggers**: 
     - Periodically if not otherwise run: `H/15 * * * *` (every 15 minutes)

## Configuration

### Branch Discovery

The pipeline discovers branches based on:
- **Includes**: Pattern matching branches to include (default: `*` for all)
- **Excludes**: Pattern matching branches to exclude (optional)

Example patterns:
- `feature/*` - All feature branches
- `bugfix/*` - All bugfix branches
- `develop` - Develop branch
- `main` or `master` - Main branch
- `release/*` - Release branches

### Environment Variables

The pipeline sets these environment variables automatically:
- `BRANCH_NAME` - Current branch name
- `BRANCH_TYPE` - Detected branch type (feature, bugfix, develop, main, other)
- `APPLICATION` - Detected application name
- `BUILD_TAG` - Build tag (branch-buildNumber)
- `IMAGE_TAG` - Docker image tag

### Docker Configuration

Update these in the Jenkinsfile:
```groovy
environment {
    DOCKER_REGISTRY = 'your-registry.io'
    DOCKER_CREDENTIALS = 'docker-registry'
}
```

### Git Configuration

Update these in the Jenkinsfile:
```groovy
environment {
    GIT_REPO = 'https://github.com/your-org/your-repo.git'
    GIT_CREDENTIALS = 'github-token'
}
```

## Customization

### Add New Branch Types

Edit the `BRANCH_TYPE` detection in `Jenkinsfile`:
```groovy
BRANCH_TYPE = "${env.BRANCH_NAME.startsWith('release/') ? 'release' : 
               env.BRANCH_NAME.startsWith('hotfix/') ? 'hotfix' : 
               ...}"
```

### Add New Stages

Add stages to the pipeline in `Jenkinsfile`:
```groovy
stage('Your Stage') {
    when {
        expression { env.BRANCH_TYPE == 'your-type' }
    }
    steps {
        // Your steps
    }
}
```

### Customize Deployment

Modify the deployment stages to match your infrastructure:
- Update `deploy.sh` scripts in application directories
- Add ArgoCD sync steps
- Add Kubernetes deployment steps

## Troubleshooting

### Branches Not Discovered

1. Check branch source configuration
2. Verify credentials have access to repository
3. Check branch name patterns in includes/excludes
4. Manually trigger branch scan: Job → "Scan Multibranch Pipeline Now"

### Build Failures

1. Check Jenkinsfile syntax
2. Verify application structure matches expected format
3. Check Docker daemon availability
4. Review build logs for specific errors

### Deployment Issues

1. Verify deployment scripts exist and are executable
2. Check environment variables are set correctly
3. Verify credentials for registry and Git
4. Check network connectivity to deployment targets

## Best Practices

1. **Branch Naming**: Use consistent naming conventions (feature/, bugfix/, release/)
2. **Jenkinsfile Location**: Keep Jenkinsfile in a consistent location across branches
3. **Credentials**: Use Jenkins credentials store, never hardcode secrets
4. **Logging**: Use descriptive echo statements for debugging
5. **Artifacts**: Archive important build artifacts for later reference
6. **Cleanup**: Clean up Docker images and workspace after builds

## Examples

### Example Branch Workflow

```
Developer creates feature branch:
  feature/user-authentication
    ↓
Jenkins automatically discovers branch
    ↓
Creates job: multibranch-pipeline-example/feature/user-authentication
    ↓
Runs pipeline:
  - Checkout
  - Validate
  - Build
  - Test
  - Build Docker Image (local)
  - Create PR Comment
    ↓
Developer creates PR
    ↓
PR is tested automatically
    ↓
After merge to develop:
  - Auto-deploys to DEV
    ↓
After merge to main:
  - Requires approval
  - Deploys to production
```

## Related Documentation

- [Jenkins Multibranch Pipeline Documentation](https://www.jenkins.io/doc/book/pipeline/multibranch/)
- [Pipeline Syntax Reference](https://www.jenkins.io/doc/book/pipeline/syntax/)
- [Branch Source Plugin](https://plugins.jenkins.io/branch-api/)

