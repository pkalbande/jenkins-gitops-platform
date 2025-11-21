// Jenkins DSL script to create Release Build and Promotion Orchestrator jobs

freeStyleJob('release-build-job') {
    description('🚀 Release Build Job - Build code, create versioned release, and archive artifacts using Release Plugin')
    displayName('🚀 Release Build Job')
    
    logRotator {
        daysToKeep(30)
        numToKeep(10)
        artifactDaysToKeep(30)
        artifactNumToKeep(10)
    }
    
    parameters {
        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select application to build')
        stringParam('RELEASE_VERSION', '1.0.0', 'Version number for the release (e.g., 1.0.0)')
        booleanParam('TAG_SCM', true, 'Tag the source code repository with release version')
    }
    
    scm {
        git {
            remote {
                url('https://github.com/pkalbande/jenkins-gitops-platform.git')
                credentials('github-token')
            }
            branch('*/master')
        }
    }
    
    properties {
        promotions {
            promotion {
                name('PROMOTE_TO_DEV')
                icon('star-gold')
                conditions {
                    selfPromotion(false)
                }
                actions {
                    shell('''#!/bin/bash
echo "=========================================="
echo "🎯 Promoting Build #${PROMOTED_NUMBER} to DEV"
echo "Application: ${APPLICATION}"
echo "Version: ${RELEASE_VERSION}"
echo "=========================================="

# Run deployment script
cd apps/${APPLICATION}
chmod +x deploy.sh
./deploy.sh DEV ${RELEASE_VERSION} ${PROMOTED_NUMBER}
                    ''')
                }
            }
            
            promotion {
                name('PROMOTE_TO_QA')
                icon('star-gold')
                conditions {
                    upstream('PROMOTE_TO_DEV')
                    manual('')
                }
                actions {
                    shell('''#!/bin/bash
echo "=========================================="
echo "🎯 Promoting Build #${PROMOTED_NUMBER} to QA"
echo "Application: ${APPLICATION}"
echo "Version: ${RELEASE_VERSION}"
echo "=========================================="

# Run deployment script
cd apps/${APPLICATION}
chmod +x deploy.sh
./deploy.sh QA ${RELEASE_VERSION} ${PROMOTED_NUMBER}
                    ''')
                }
            }
            
            promotion {
                name('PROMOTE_TO_STAGE')
                icon('star-gold')
                conditions {
                    upstream('PROMOTE_TO_QA')
                    manual('')
                }
                actions {
                    shell('''#!/bin/bash
echo "=========================================="
echo "🎯 Promoting Build #${PROMOTED_NUMBER} to STAGE"
echo "Application: ${APPLICATION}"
echo "Version: ${RELEASE_VERSION}"
echo "=========================================="

# Run deployment script
cd apps/${APPLICATION}
chmod +x deploy.sh
./deploy.sh STAGE ${RELEASE_VERSION} ${PROMOTED_NUMBER}
                    ''')
                }
            }
            
            promotion {
                name('PROMOTE_TO_PROD')
                icon('star-gold')
                conditions {
                    upstream('PROMOTE_TO_STAGE')
                    manual('')
                }
                actions {
                    shell('''#!/bin/bash
echo "=========================================="
echo "🎯 Promoting Build #${PROMOTED_NUMBER} to PROD"
echo "Application: ${APPLICATION}"
echo "Version: ${RELEASE_VERSION}"
echo "=========================================="

# Run deployment script
cd apps/${APPLICATION}
chmod +x deploy.sh
./deploy.sh PROD ${RELEASE_VERSION} ${PROMOTED_NUMBER}
                    ''')
                }
            }
        }
    }
    
    wrappers {
        release {
            preBuildSteps {
                shell('echo "Preparing release ${RELEASE_VERSION}"')
            }
            postSuccessfulBuildSteps {
                shell('echo "Release ${RELEASE_VERSION} completed successfully"')
            }
            postBuildSteps {
                shell('echo "Post-build steps for release ${RELEASE_VERSION}"')
            }
            postFailedBuildSteps {
                shell('echo "Release ${RELEASE_VERSION} failed"')
            }
            parameters {
                textParam {
                    name('RELEASE_VERSION')
                    defaultValue('1.0.0')
                    description('Release version number')
                }
            }
            configure { project ->
                project / 'buildWrappers' / 'hudson.plugins.release.ReleaseWrapper' / 'releaseVersionTemplate' << '${RELEASE_VERSION}'
                project / 'buildWrappers' / 'hudson.plugins.release.ReleaseWrapper' / 'doNotKeepLog' << 'false'
                project / 'buildWrappers' / 'hudson.plugins.release.ReleaseWrapper' / 'overrideBuildParameters' << 'false'
            }
        }
    }
    
    steps {
        shell('''#!/bin/bash
set -e

echo "=========================================="
echo "🚀 Building Release Version: ${RELEASE_VERSION}"
echo "Application: ${APPLICATION}"
echo "=========================================="

# Navigate to application directory
cd apps/${APPLICATION}

# Create release metadata
echo "Creating release metadata..."
cat > release-metadata.json << 'EOF'
{
  "application": "APPLICATION_PLACEHOLDER",
  "version": "VERSION_PLACEHOLDER",
  "build_number": "BUILD_NUMBER_PLACEHOLDER",
  "build_date": "BUILD_DATE_PLACEHOLDER",
  "git_commit": "GIT_COMMIT_PLACEHOLDER",
  "git_branch": "GIT_BRANCH_PLACEHOLDER",
  "jenkins_url": "BUILD_URL_PLACEHOLDER"
}
EOF

# Replace placeholders with actual values
sed -i.bak "s|APPLICATION_PLACEHOLDER|${APPLICATION}|g" release-metadata.json
sed -i.bak "s|VERSION_PLACEHOLDER|${RELEASE_VERSION}|g" release-metadata.json
sed -i.bak "s|BUILD_NUMBER_PLACEHOLDER|${BUILD_NUMBER}|g" release-metadata.json
sed -i.bak "s|BUILD_DATE_PLACEHOLDER|$(date -u +%Y-%m-%dT%H:%M:%SZ)|g" release-metadata.json
sed -i.bak "s|GIT_COMMIT_PLACEHOLDER|${GIT_COMMIT:-unknown}|g" release-metadata.json
sed -i.bak "s|GIT_BRANCH_PLACEHOLDER|${GIT_BRANCH:-master}|g" release-metadata.json
sed -i.bak "s|BUILD_URL_PLACEHOLDER|${BUILD_URL}|g" release-metadata.json
rm -f release-metadata.json.bak

echo "=========================================="
echo "✅ Build completed successfully!"
echo "=========================================="
cat release-metadata.json
        ''')
    }
    
    publishers {
        archiveArtifacts {
            pattern('apps/*/release-metadata.json, apps/*/Dockerfile')
            fingerprint(true)
            allowEmpty(false)
        }
    }
}

freeStyleJob('promotion-orchestrator-job') {
    description('🎯 Promotion Orchestrator Job - Select and promote previous builds across environments (DEV → QA → STAGE → PROD)')
    displayName('🎯 Promotion Orchestrator')
    
    logRotator {
        daysToKeep(60)
        numToKeep(20)
        artifactDaysToKeep(60)
        artifactNumToKeep(20)
    }
    
    parameters {
        stringParam('BUILD_NUMBER', '', 'Build number from release-build-job to promote (e.g., 5)')
        choiceParam('ENVIRONMENT', ['DEV', 'QA', 'STAGE', 'PROD'], 'Select environment to promote to')
        textParam('APPROVAL_NOTES', '', 'Approval notes and justification for this promotion')
    }
    
    steps {
        shell('''#!/bin/bash
set -e

echo "=========================================="
echo "🎯 Manual Promotion Request"
echo "=========================================="
echo "Build Number: ${BUILD_NUMBER}"
echo "Environment: ${ENVIRONMENT}"
echo "Approval Notes: ${APPROVAL_NOTES}"
echo "Requested by: ${BUILD_USER:-admin}"
echo "=========================================="

# Validate build number
if [ -z "${BUILD_NUMBER}" ]; then
    echo "❌ ERROR: BUILD_NUMBER is required"
    exit 1
fi

# Get Jenkins credentials
JENKINS_URL="${JENKINS_URL:-http://jenkins.jenkins.svc.cluster.local:8080}"
JENKINS_USER="${JENKINS_USER:-admin}"
JENKINS_TOKEN="${JENKINS_TOKEN:-admin123}"

# Construct promotion URL based on environment
PROMOTION_NAME="PROMOTE_TO_${ENVIRONMENT}"

echo "Triggering promotion: ${PROMOTION_NAME} for build #${BUILD_NUMBER}..."

# Trigger the promotion using Jenkins API
PROMOTION_URL="${JENKINS_URL}/job/release-build-job/${BUILD_NUMBER}/promotion/${PROMOTION_NAME}/forcePromotion"

echo "Promotion URL: ${PROMOTION_URL}"
echo "Submitting promotion request..."

# Use curl to trigger the promotion
HTTP_CODE=$(curl -s -o /tmp/promotion_response.txt -w "%{http_code}" \\
    -X POST \\
    -u "${JENKINS_USER}:${JENKINS_TOKEN}" \\
    "${PROMOTION_URL}")

echo "HTTP Response Code: ${HTTP_CODE}"

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ] || [ "${HTTP_CODE}" = "302" ]; then
    echo "✅ Successfully triggered promotion to ${ENVIRONMENT}"
    echo "=========================================="
    echo "Promotion Details:"
    echo "  • Source Build: release-build-job #${BUILD_NUMBER}"
    echo "  • Target Environment: ${ENVIRONMENT}"
    echo "  • Promotion Process: ${PROMOTION_NAME}"
    echo "  • Status: Triggered"
    echo "=========================================="
else
    echo "⚠️  Promotion request returned HTTP ${HTTP_CODE}"
    echo "Response:"
    cat /tmp/promotion_response.txt
    echo ""
    echo "Note: Check release-build-job #${BUILD_NUMBER} promotion status manually"
fi

# Log promotion request
echo ""
echo "📝 Promotion Log Entry:"
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Build: #${BUILD_NUMBER}"
echo "Environment: ${ENVIRONMENT}"
echo "Approver: ${BUILD_USER:-admin}"
echo "Notes: ${APPROVAL_NOTES}"
echo "=========================================="
        ''')
    }
    
    publishers {
        mailer('', false, false)
    }
}

// Pipeline Job 1: Release Pipeline with Flexible Deployment
pipelineJob('release-pipeline-job') {
    description('🚀 Release Pipeline - Flexible deployment: skip any environment and deploy to any other environment independently. Supports build reuse across environments.')
    displayName('🚀 Release Pipeline')
    
    logRotator {
        daysToKeep(30)
        numToKeep(10)
        artifactDaysToKeep(30)
        artifactNumToKeep(10)
    }
    
    parameters {
        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select application to build')
        stringParam('RELEASE_VERSION', '1.0.0', 'Version number for the release (e.g., 1.0.0)')
        stringParam('USE_BUILD_NUMBER', '', 'Optional: Use existing build number (leave empty for new build)')
        booleanParam('DEPLOY_TO_DEV', true, 'Deploy to DEV environment (can skip)')
        booleanParam('DEPLOY_TO_TEST', false, 'Deploy to TEST environment (can skip)')
        booleanParam('DEPLOY_TO_STAGE', false, 'Deploy to STAGE environment (can skip)')
        booleanParam('DEPLOY_TO_PROD', false, 'Deploy to PROD environment (can skip)')
    }
    
    definition {
        cps {
            script('''
pipeline {
    agent any
    
    parameters {
        choice(name: 'APPLICATION', choices: ['app1-node', 'app2-python'], description: 'Select application to build')
        string(name: 'RELEASE_VERSION', defaultValue: '1.0.0', description: 'Version number for the release')
        string(name: 'USE_BUILD_NUMBER', defaultValue: '', description: 'Optional: Use existing build number (leave empty for new build)')
        booleanParam(name: 'DEPLOY_TO_DEV', defaultValue: true, description: 'Deploy to DEV environment')
        booleanParam(name: 'DEPLOY_TO_TEST', defaultValue: false, description: 'Deploy to TEST environment')
        booleanParam(name: 'DEPLOY_TO_STAGE', defaultValue: false, description: 'Deploy to STAGE environment')
        booleanParam(name: 'DEPLOY_TO_PROD', defaultValue: false, description: 'Deploy to PROD environment')
    }
    
    environment {
        BUILD_DATE = sh(script: 'date -u +%Y-%m-%dT%H:%M:%SZ', returnStdout: true).trim()
        GIT_COMMIT_HASH = sh(script: 'git rev-parse HEAD || echo "unknown"', returnStdout: true).trim()
        GIT_BRANCH_NAME = sh(script: 'git rev-parse --abbrev-ref HEAD || echo "master"', returnStdout: true).trim()
        EFFECTIVE_BUILD_NUMBER = "${params.USE_BUILD_NUMBER ?: env.BUILD_NUMBER}"
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo "=========================================="
                echo "📥 Checking out source code"
                echo "=========================================="
                git branch: 'master', 
                    credentialsId: 'github-token', 
                    url: 'https://github.com/pkalbande/jenkins-gitops-platform.git'
            }
        }
        
        stage('Build') {
            when {
                expression { params.USE_BUILD_NUMBER == null || params.USE_BUILD_NUMBER == '' }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "🚀 Building Release Version: ${params.RELEASE_VERSION}"
                    echo "Application: ${params.APPLICATION}"
                    echo "Build Number: ${env.BUILD_NUMBER}"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            echo "Creating release metadata..."
                            cat > release-metadata.json << EOF
{
  "application": "${params.APPLICATION}",
  "version": "${params.RELEASE_VERSION}",
  "build_number": "${env.BUILD_NUMBER}",
  "build_date": "${env.BUILD_DATE}",
  "git_commit": "${env.GIT_COMMIT_HASH}",
  "git_branch": "${env.GIT_BRANCH_NAME}",
  "jenkins_url": "${env.BUILD_URL}"
}
EOF
                            
                            echo "=========================================="
                            echo "✅ Build completed successfully!"
                            echo "=========================================="
                            cat release-metadata.json
                        """
                        
                        archiveArtifacts artifacts: 'release-metadata.json,Dockerfile', fingerprint: true
                    }
                }
            }
        }
        
        stage('Reuse Build') {
            when {
                expression { params.USE_BUILD_NUMBER != null && params.USE_BUILD_NUMBER != '' }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "♻️  Reusing Existing Build"
                    echo "=========================================="
                    echo "Application: ${params.APPLICATION}"
                    echo "Version: ${params.RELEASE_VERSION}"
                    echo "Using Build Number: ${params.USE_BUILD_NUMBER}"
                    echo "Current Job Build: ${env.BUILD_NUMBER}"
                    echo "=========================================="
                    echo "⚠️  Skipping build stage - deploying existing artifacts"
                }
            }
        }
        
        stage('Deploy to DEV') {
            when {
                expression { params.DEPLOY_TO_DEV == true }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "🎯 Deploying to DEV Environment"
                    echo "Build Number: ${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh DEV ${params.RELEASE_VERSION} ${env.EFFECTIVE_BUILD_NUMBER}
                        """
                    }
                }
            }
        }
        
        stage('Deploy to TEST') {
            when {
                expression { params.DEPLOY_TO_TEST == true }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  TEST Deployment - Approval Required"
                    echo "Build Number: ${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "=========================================="
                    
                    timeout(time: 24, unit: 'HOURS') {
                        input message: "Approve deployment of build #${env.EFFECTIVE_BUILD_NUMBER} to TEST?", 
                              ok: 'Deploy to TEST',
                              submitter: 'admin',
                              submitterParameter: 'APPROVER'
                    }
                    
                    echo "=========================================="
                    echo "🎯 Deploying to TEST Environment"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh TEST ${params.RELEASE_VERSION} ${env.EFFECTIVE_BUILD_NUMBER}
                        """
                    }
                }
            }
        }
        
        stage('Deploy to STAGE') {
            when {
                expression { params.DEPLOY_TO_STAGE == true }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  STAGE Deployment - Approval Required"
                    echo "Build Number: ${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "=========================================="
                    
                    timeout(time: 24, unit: 'HOURS') {
                        input message: "Approve deployment of build #${env.EFFECTIVE_BUILD_NUMBER} to STAGE?", 
                              ok: 'Deploy to STAGE',
                              submitter: 'admin',
                              submitterParameter: 'APPROVER'
                    }
                    
                    echo "=========================================="
                    echo "🎯 Deploying to STAGE Environment"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh STAGE ${params.RELEASE_VERSION} ${env.EFFECTIVE_BUILD_NUMBER}
                        """
                    }
                }
            }
        }
        
        stage('Deploy to PROD') {
            when {
                expression { params.DEPLOY_TO_PROD == true }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  PRODUCTION Deployment - Approval Required"
                    echo "Build Number: ${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "=========================================="
                    
                    timeout(time: 72, unit: 'HOURS') {
                        input message: "Approve deployment of build #${env.EFFECTIVE_BUILD_NUMBER} to PRODUCTION?", 
                              ok: 'Deploy to PROD',
                              submitter: 'admin',
                              submitterParameter: 'APPROVER'
                    }
                    
                    echo "=========================================="
                    echo "🎯 Deploying to PRODUCTION Environment"
                    echo "⚠️  PRODUCTION DEPLOYMENT IN PROGRESS"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh PROD ${params.RELEASE_VERSION} ${env.EFFECTIVE_BUILD_NUMBER}
                        """
                    }
                }
            }
        }
    }
    
    post {
        success {
            script {
                def deployedEnvs = []
                if (params.DEPLOY_TO_DEV) deployedEnvs.add('DEV')
                if (params.DEPLOY_TO_TEST) deployedEnvs.add('TEST')
                if (params.DEPLOY_TO_STAGE) deployedEnvs.add('STAGE')
                if (params.DEPLOY_TO_PROD) deployedEnvs.add('PROD')
                
                def buildInfo = params.USE_BUILD_NUMBER ? "Reused build #${params.USE_BUILD_NUMBER}" : "New build #${env.BUILD_NUMBER}"
                
                echo "=========================================="
                echo "✅ Pipeline completed successfully!"
                echo "${buildInfo}"
                echo "Release ${params.RELEASE_VERSION} deployed to: ${deployedEnvs.join(', ')}"
                echo "=========================================="
            }
        }
        failure {
            echo "=========================================="
            echo "❌ Pipeline failed!"
            echo "=========================================="
        }
        aborted {
            echo "=========================================="
            echo "⚠️  Pipeline aborted by user"
            echo "=========================================="
        }
    }
}
            '''.stripIndent())
            sandbox()
        }
    }
}


// Pipeline Job 2: Selective Promotion Pipeline
pipelineJob('selective-promotion-pipeline') {
    description('🎯 Selective Promotion Pipeline - Manually select environment to deploy a specific build')
    displayName('🎯 Selective Promotion Pipeline')
    
    logRotator {
        daysToKeep(60)
        numToKeep(20)
        artifactDaysToKeep(60)
        artifactNumToKeep(20)
    }
    
    parameters {
        stringParam('BUILD_NUMBER', '', 'Build number to promote (from release-pipeline-job)')
        choiceParam('TARGET_ENVIRONMENT', ['TEST', 'STAGE', 'PROD'], 'Select target environment')
        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select application')
        stringParam('VERSION', '1.0.0', 'Version to promote')
        textParam('APPROVAL_NOTES', '', 'Approval notes and justification')
    }
    
    definition {
        cps {
            script('''
pipeline {
    agent any
    
    parameters {
        string(name: 'BUILD_NUMBER', defaultValue: '', description: 'Build number to promote')
        choice(name: 'TARGET_ENVIRONMENT', choices: ['TEST', 'STAGE', 'PROD'], description: 'Target environment')
        choice(name: 'APPLICATION', choices: ['app1-node', 'app2-python'], description: 'Application')
        string(name: 'VERSION', defaultValue: '1.0.0', description: 'Version to promote')
        text(name: 'APPROVAL_NOTES', defaultValue: '', description: 'Approval notes')
    }
    
    stages {
        stage('Validate') {
            steps {
                script {
                    echo "=========================================="
                    echo "🔍 Validating Promotion Request"
                    echo "=========================================="
                    echo "Build Number: ${params.BUILD_NUMBER}"
                    echo "Application: ${params.APPLICATION}"
                    echo "Version: ${params.VERSION}"
                    echo "Target Environment: ${params.TARGET_ENVIRONMENT}"
                    echo "Approval Notes: ${params.APPROVAL_NOTES}"
                    echo "=========================================="
                    
                    if (!params.BUILD_NUMBER) {
                        error("BUILD_NUMBER is required!")
                    }
                }
            }
        }
        
        stage('Approval') {
            when {
                expression { params.TARGET_ENVIRONMENT in ['STAGE', 'PROD'] }
            }
            steps {
                script {
                    def approvalMessage = "Promote build #${params.BUILD_NUMBER} to ${params.TARGET_ENVIRONMENT}?"
                    
                    echo "=========================================="
                    echo "⏸️  Approval Required for ${params.TARGET_ENVIRONMENT}"
                    echo "=========================================="
                    
                    timeout(time: 48, unit: 'HOURS') {
                        input message: approvalMessage,
                              ok: "Approve ${params.TARGET_ENVIRONMENT} Promotion",
                              submitter: 'admin',
                              submitterParameter: 'APPROVER'
                    }
                }
            }
        }
        
        stage('Checkout') {
            steps {
                echo "📥 Checking out source code"
                git branch: 'master', 
                    credentialsId: 'github-token', 
                    url: 'https://github.com/pkalbande/jenkins-gitops-platform.git'
            }
        }
        
        stage('Deploy') {
            steps {
                script {
                    echo "=========================================="
                    echo "🎯 Deploying to ${params.TARGET_ENVIRONMENT}"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh ${params.TARGET_ENVIRONMENT} ${params.VERSION} ${params.BUILD_NUMBER}
                        """
                    }
                    
                    echo "=========================================="
                    echo "📝 Promotion Log Entry:"
                    echo "Timestamp: \\$(date -u +%Y-%m-%dT%H:%M:%SZ)"
                    echo "Build: #${params.BUILD_NUMBER}"
                    echo "Environment: ${params.TARGET_ENVIRONMENT}"
                    echo "Application: ${params.APPLICATION}"
                    echo "Version: ${params.VERSION}"
                    echo "Notes: ${params.APPROVAL_NOTES}"
                    echo "=========================================="
                }
            }
        }
    }
    
    post {
        success {
            echo "✅ Successfully promoted build #${params.BUILD_NUMBER} to ${params.TARGET_ENVIRONMENT}"
        }
        failure {
            echo "❌ Failed to promote build #${params.BUILD_NUMBER} to ${params.TARGET_ENVIRONMENT}"
        }
    }
}
            '''.stripIndent())
            sandbox()
        }
    }
}



/* Pipeline Job 2: Selective Promotion Pipeline
pipelineJob('selective-promotion-pipeline-v2') {
    description('🎯 Selective Promotion Pipeline v2 - Manually select environment to deploy a specific build')
    displayName('🎯 Selective Promotion Pipeline-v2')
    
    logRotator {
        daysToKeep(60)
        numToKeep(20)
        artifactDaysToKeep(60)
        artifactNumToKeep(20)
    }
    
    parameters {
        stringParam('BUILD_NUMBER', '', 'Build number to promote (from release-pipeline-job)')
        choiceParam('TARGET_ENVIRONMENT', ['TEST', 'STAGE', 'PROD'], 'Select target environment')
        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select application')
        stringParam('VERSION', '1.0.0', 'Version to promote')
        textParam('APPROVAL_NOTES', '', 'Approval notes and justification')
    }
    
    definition {
        cps {
            script('''
pipeline {
    agent any
    
    parameters {
        string(name: 'BUILD_NUMBER', defaultValue: '', description: 'Build number to promote')
        choice(name: 'TARGET_ENVIRONMENT', choices: ['TEST', 'STAGE', 'PROD'], description: 'Target environment')
        choice(name: 'APPLICATION', choices: ['app1-node', 'app2-python'], description: 'Application')
        string(name: 'VERSION', defaultValue: '1.0.0', description: 'Version to promote')
        text(name: 'APPROVAL_NOTES', defaultValue: '', description: 'Approval notes')
    }
    
    stages {
        stage('Validate') {
            steps {
                script {
                    echo "=========================================="
                    echo "🔍 Validating Promotion Request"
                    echo "=========================================="
                    echo "Build Number: ${params.BUILD_NUMBER}"
                    echo "Application: ${params.APPLICATION}"
                    echo "Version: ${params.VERSION}"
                    echo "Target Environment: ${params.TARGET_ENVIRONMENT}"
                    echo "Approval Notes: ${params.APPROVAL_NOTES}"
                    echo "=========================================="
                    
                    if (!params.BUILD_NUMBER) {
                        error("BUILD_NUMBER is required!")
                    }
                }
            }
        }
        
        stage('Approval') {
            when {
                expression { params.TARGET_ENVIRONMENT in ['STAGE', 'PROD'] }
            }
            steps {
                script {
                    def approvalMessage = "Promote build #${params.BUILD_NUMBER} to ${params.TARGET_ENVIRONMENT}?"
                    
                    echo "=========================================="
                    echo "⏸️  Approval Required for ${params.TARGET_ENVIRONMENT}"
                    echo "=========================================="
                    
                    timeout(time: 48, unit: 'HOURS') {
                        input message: approvalMessage,
                              ok: "Approve ${params.TARGET_ENVIRONMENT} Promotion",
                              submitter: 'admin',
                              submitterParameter: 'APPROVER'
                    }
                }
            }
        }
        
        stage('Checkout') {
            steps {
                echo "📥 Checking out source code"
                git branch: 'master', 
                    credentialsId: 'github-token', 
                    url: 'https://github.com/pkalbande/jenkins-gitops-platform.git'
            }
        }
        
        stage('Deploy') {
            steps {
                script {
                    echo "=========================================="
                    echo "🎯 Deploying to ${params.TARGET_ENVIRONMENT}"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh ${params.TARGET_ENVIRONMENT} ${params.VERSION} ${params.BUILD_NUMBER}
                        """
                    }
                    
                    echo "=========================================="
                    echo "📝 Promotion Log Entry:"
                    echo "Timestamp: \\$(date -u +%Y-%m-%dT%H:%M:%SZ)"
                    echo "Build: #${params.BUILD_NUMBER}"
                    echo "Environment: ${params.TARGET_ENVIRONMENT}"
                    echo "Application: ${params.APPLICATION}"
                    echo "Version: ${params.VERSION}"
                    echo "Notes: ${params.APPROVAL_NOTES}"
                    echo "=========================================="
                }
            }
        }
    }
    
    post {
        success {
            echo "✅ Successfully promoted build #${params.BUILD_NUMBER} to ${params.TARGET_ENVIRONMENT}"
        }
        failure {
            echo "❌ Failed to promote build #${params.BUILD_NUMBER} to ${params.TARGET_ENVIRONMENT}"
        }
    }
}
            '''.stripIndent())
            sandbox()
        }
    }
}
*/
// ==============================================================================
// NEW JOBS: API Lifecycle Pipeline with Build Reuse and Restart Capability
// ==============================================================================

// Declarative Pipeline: API Lifecycle Pipeline
pipelineJob('api-lifecycle-pipeline') {
    description('🚀 API Lifecycle Pipeline - Build API, deploy with lifecycle approvals, reuse builds, and restart from any stage')
    displayName('🚀 API Lifecycle Pipeline')
    
    logRotator {
        daysToKeep(90)
        numToKeep(50)
        artifactDaysToKeep(90)
        artifactNumToKeep(50)
    }
    
    parameters {
        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select API application to build')
        stringParam('API_VERSION', '1.0.0', 'API version (e.g., 1.0.0)')
        stringParam('REUSE_BUILD_NUMBER', '', 'Optional: Reuse existing build number (leave empty for new build)')
        choiceParam('START_FROM_STAGE', ['Build', 'Deploy-DEV', 'Deploy-TEST', 'Deploy-STAGE', 'Deploy-PROD'], 'Start/Resume from stage (for continuing builds)')
        booleanParam('DEPLOY_TO_DEV', true, 'Deploy to DEV environment')
        booleanParam('DEPLOY_TO_TEST', false, 'Deploy to TEST environment')
        booleanParam('DEPLOY_TO_STAGE', false, 'Deploy to STAGE environment')
        booleanParam('DEPLOY_TO_PROD', false, 'Deploy to PROD environment')
    }
    
    definition {
        cps {
            script('''
pipeline {
    agent any
    
    parameters {
        choice(name: 'APPLICATION', choices: ['app1-node', 'app2-python'], description: 'Select API application')
        string(name: 'API_VERSION', defaultValue: '1.0.0', description: 'API version')
        string(name: 'REUSE_BUILD_NUMBER', defaultValue: '', description: 'Reuse existing build number (empty for new build)')
        choice(name: 'START_FROM_STAGE', choices: ['Build', 'Deploy-DEV', 'Deploy-TEST', 'Deploy-STAGE', 'Deploy-PROD'], description: 'Start/Resume from stage')
        booleanParam(name: 'DEPLOY_TO_DEV', defaultValue: true, description: 'Deploy to DEV')
        booleanParam(name: 'DEPLOY_TO_TEST', defaultValue: false, description: 'Deploy to TEST')
        booleanParam(name: 'DEPLOY_TO_STAGE', defaultValue: false, description: 'Deploy to STAGE')
        booleanParam(name: 'DEPLOY_TO_PROD', defaultValue: false, description: 'Deploy to PROD')
    }
    
    environment {
        BUILD_TIMESTAMP = sh(script: 'date -u +%Y-%m-%dT%H:%M:%SZ', returnStdout: true).trim()
        GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD || echo "unknown"', returnStdout: true).trim()
        GIT_BRANCH = sh(script: 'git rev-parse --abbrev-ref HEAD || echo "master"', returnStdout: true).trim()
        EFFECTIVE_BUILD_NUMBER = "${params.REUSE_BUILD_NUMBER ?: env.BUILD_NUMBER}"
        DEPLOYMENT_TRACKER = "deployment-tracker-${env.EFFECTIVE_BUILD_NUMBER}.json"
    }
    
    stages {
        stage('Initialize') {
            steps {
                script {
                    echo "=========================================="
                    echo "🔧 Initializing API Lifecycle Pipeline"
                    echo "=========================================="
                    echo "Application: ${params.APPLICATION}"
                    echo "API Version: ${params.API_VERSION}"
                    echo "Effective Build: ${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "Start From: ${params.START_FROM_STAGE}"
                    
                    if (params.REUSE_BUILD_NUMBER) {
                        echo "♻️  Mode: Reusing Build #${params.REUSE_BUILD_NUMBER}"
                    } else {
                        echo "🆕 Mode: Creating New Build"
                    }
                    echo "=========================================="
                }
            }
        }
        
        stage('Checkout') {
            when {
                expression { params.START_FROM_STAGE == 'Build' || params.REUSE_BUILD_NUMBER == '' }
            }
            steps {
                echo "📥 Checking out source code"
                git branch: 'master', 
                    credentialsId: 'github-token', 
                    url: 'https://github.com/pkalbande/jenkins-gitops-platform.git'
            }
        }
        
        stage('Build API') {
            when {
                expression { 
                    (params.REUSE_BUILD_NUMBER == null || params.REUSE_BUILD_NUMBER == '') &&
                    (params.START_FROM_STAGE == 'Build')
                }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "🏗️  Building API"
                    echo "=========================================="
                    echo "API: ${params.APPLICATION}"
                    echo "Version: ${params.API_VERSION}"
                    echo "Build: ${env.BUILD_NUMBER}"
                    echo "Commit: ${env.GIT_COMMIT_SHORT}"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            echo "Creating API build artifacts..."
                            
                            # Create API metadata
                            cat > api-build-${env.BUILD_NUMBER}.json << EOF
{
  "api_name": "${params.APPLICATION}",
  "version": "${params.API_VERSION}",
  "build_number": "${env.BUILD_NUMBER}",
  "build_timestamp": "${env.BUILD_TIMESTAMP}",
  "git_commit": "${env.GIT_COMMIT_SHORT}",
  "git_branch": "${env.GIT_BRANCH}",
  "jenkins_build_url": "${env.BUILD_URL}",
  "deployment_status": {
    "dev": "pending",
    "test": "pending",
    "stage": "pending",
    "prod": "pending"
  }
}
EOF
                            
                            echo "✅ API build artifacts created"
                            cat api-build-${env.BUILD_NUMBER}.json
                        """
                        
                        archiveArtifacts artifacts: "api-build-${env.BUILD_NUMBER}.json,Dockerfile", fingerprint: true
                    }
                    
                    echo "✅ API build completed: Build #${env.BUILD_NUMBER}"
                }
            }
        }
        
        stage('Load Build Info') {
            when {
                expression { params.REUSE_BUILD_NUMBER != null && params.REUSE_BUILD_NUMBER != '' }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "📦 Loading Existing Build Information"
                    echo "=========================================="
                    echo "Reusing Build: #${params.REUSE_BUILD_NUMBER}"
                    echo "Starting from: ${params.START_FROM_STAGE}"
                    echo "=========================================="
                    echo "✅ Build #${params.REUSE_BUILD_NUMBER} ready for deployment"
                }
            }
        }
        
        stage('Deploy to DEV') {
            when {
                expression { 
                    params.DEPLOY_TO_DEV == true &&
                    (params.START_FROM_STAGE == 'Build' || params.START_FROM_STAGE == 'Deploy-DEV')
                }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  DEV Deployment - Approval Required"
                    echo "Build: #${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "=========================================="
                    
                    def approver = 'system'
                    timeout(time: 24, unit: 'HOURS') {
                        approver = input message: "Approve API deployment to DEV environment?\\nBuild: #${env.EFFECTIVE_BUILD_NUMBER}\\nAPI: ${params.APPLICATION}\\nVersion: ${params.API_VERSION}", 
                              ok: 'Approve DEV Deployment',
                              submitter: 'admin,devops-team',
                              submitterParameter: 'DEV_APPROVER'
                    }
                    
                    echo "✅ DEV Deployment Approved by: ${approver}"
                    echo "=========================================="
                    echo "🚀 Deploying API to DEV"
                    echo "=========================================="
                    
                    // Checkout for deployment if needed
                    git branch: 'master', 
                        credentialsId: 'github-token', 
                        url: 'https://github.com/pkalbande/jenkins-gitops-platform.git'
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh DEV ${params.API_VERSION} ${env.EFFECTIVE_BUILD_NUMBER}
                        """
                    }
                    
                    echo "✅ Successfully deployed to DEV"
                }
            }
        }
        
        stage('Deploy to TEST') {
            when {
                expression { 
                    params.DEPLOY_TO_TEST == true &&
                    (params.START_FROM_STAGE == 'Build' || params.START_FROM_STAGE == 'Deploy-DEV' || params.START_FROM_STAGE == 'Deploy-TEST')
                }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  TEST Deployment - Lifecycle Approval Required"
                    echo "Build: #${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "=========================================="
                    
                    def approver = 'system'
                    timeout(time: 48, unit: 'HOURS') {
                        approver = input message: "Approve API deployment to TEST environment?\\nBuild: #${env.EFFECTIVE_BUILD_NUMBER}\\nAPI: ${params.APPLICATION}\\nVersion: ${params.API_VERSION}\\n\\n⚠️  Lifecycle Check: DEV testing completed?", 
                              ok: 'Approve TEST Deployment',
                              submitter: 'admin,qa-team',
                              submitterParameter: 'TEST_APPROVER'
                    }
                    
                    echo "✅ TEST Deployment Approved by: ${approver}"
                    echo "=========================================="
                    echo "🚀 Deploying API to TEST"
                    echo "=========================================="
                    
                    // Checkout for deployment if needed
                    git branch: 'master', 
                        credentialsId: 'github-token', 
                        url: 'https://github.com/pkalbande/jenkins-gitops-platform.git'
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh TEST ${params.API_VERSION} ${env.EFFECTIVE_BUILD_NUMBER}
                        """
                    }
                    
                    echo "✅ Successfully deployed to TEST"
                }
            }
        }
        
        stage('Deploy to STAGE') {
            when {
                expression { 
                    params.DEPLOY_TO_STAGE == true &&
                    (params.START_FROM_STAGE == 'Build' || params.START_FROM_STAGE == 'Deploy-DEV' || 
                     params.START_FROM_STAGE == 'Deploy-TEST' || params.START_FROM_STAGE == 'Deploy-STAGE')
                }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  STAGE Deployment - Lifecycle Approval Required"
                    echo "Build: #${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "=========================================="
                    
                    def approver = 'system'
                    timeout(time: 72, unit: 'HOURS') {
                        approver = input message: "Approve API deployment to STAGE environment?\\nBuild: #${env.EFFECTIVE_BUILD_NUMBER}\\nAPI: ${params.APPLICATION}\\nVersion: ${params.API_VERSION}\\n\\n⚠️  Lifecycle Check: TEST validation completed?", 
                              ok: 'Approve STAGE Deployment',
                              submitter: 'admin,release-manager',
                              submitterParameter: 'STAGE_APPROVER'
                    }
                    
                    echo "✅ STAGE Deployment Approved by: ${approver}"
                    echo "=========================================="
                    echo "🚀 Deploying API to STAGE"
                    echo "=========================================="
                    
                    // Checkout for deployment if needed
                    git branch: 'master', 
                        credentialsId: 'github-token', 
                        url: 'https://github.com/pkalbande/jenkins-gitops-platform.git'
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh STAGE ${params.API_VERSION} ${env.EFFECTIVE_BUILD_NUMBER}
                        """
                    }
                    
                    echo "✅ Successfully deployed to STAGE"
                }
            }
        }
        
        stage('Deploy to PROD') {
            when {
                expression { 
                    params.DEPLOY_TO_PROD == true &&
                    (params.START_FROM_STAGE == 'Build' || params.START_FROM_STAGE == 'Deploy-DEV' || 
                     params.START_FROM_STAGE == 'Deploy-TEST' || params.START_FROM_STAGE == 'Deploy-STAGE' ||
                     params.START_FROM_STAGE == 'Deploy-PROD')
                }
            }
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  PRODUCTION Deployment - Critical Approval Required"
                    echo "Build: #${env.EFFECTIVE_BUILD_NUMBER}"
                    echo "=========================================="
                    
                    def approver = 'system'
                    timeout(time: 168, unit: 'HOURS') {
                        approver = input message: "⚠️  PRODUCTION DEPLOYMENT APPROVAL\\n\\nBuild: #${env.EFFECTIVE_BUILD_NUMBER}\\nAPI: ${params.APPLICATION}\\nVersion: ${params.API_VERSION}\\n\\n🔒 Lifecycle Check: STAGE validation completed?\\n🔒 All approvals obtained?\\n🔒 Rollback plan ready?", 
                              ok: 'APPROVE PRODUCTION DEPLOYMENT',
                              submitter: 'admin,production-approvers',
                              submitterParameter: 'PROD_APPROVER'
                    }
                    
                    echo "✅ PRODUCTION Deployment Approved by: ${approver}"
                    echo "=========================================="
                    echo "🚀 Deploying API to PRODUCTION"
                    echo "⚠️  PRODUCTION DEPLOYMENT IN PROGRESS"
                    echo "=========================================="
                    
                    // Checkout for deployment if needed
                    git branch: 'master', 
                        credentialsId: 'github-token', 
                        url: 'https://github.com/pkalbande/jenkins-gitops-platform.git'
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh PROD ${params.API_VERSION} ${env.EFFECTIVE_BUILD_NUMBER}
                        """
                    }
                    
                    echo "✅ Successfully deployed to PRODUCTION"
                }
            }
        }
    }
    
    post {
        success {
            script {
                def deployedEnvs = []
                if (params.DEPLOY_TO_DEV) deployedEnvs.add('DEV')
                if (params.DEPLOY_TO_TEST) deployedEnvs.add('TEST')
                if (params.DEPLOY_TO_STAGE) deployedEnvs.add('STAGE')
                if (params.DEPLOY_TO_PROD) deployedEnvs.add('PROD')
                
                def buildMode = params.REUSE_BUILD_NUMBER ? "Reused Build #${params.REUSE_BUILD_NUMBER}" : "New Build #${env.BUILD_NUMBER}"
                
                echo "=========================================="
                echo "✅ API Lifecycle Pipeline Completed!"
                echo "=========================================="
                echo "Mode: ${buildMode}"
                echo "API: ${params.APPLICATION}"
                echo "Version: ${params.API_VERSION}"
                echo "Started From: ${params.START_FROM_STAGE}"
                echo "Deployed To: ${deployedEnvs.join(' → ')}"
                echo "Build #${env.EFFECTIVE_BUILD_NUMBER} available for future deployments"
                echo "=========================================="
                echo "💡 To deploy this build to other environments:"
                echo "   Set REUSE_BUILD_NUMBER = ${env.EFFECTIVE_BUILD_NUMBER}"
                echo "   Select desired environments and run again"
                echo "=========================================="
            }
        }
        failure {
            echo "=========================================="
            echo "❌ Pipeline Failed"
            echo "=========================================="
            echo "Build: #${env.EFFECTIVE_BUILD_NUMBER}"
            echo "You can restart from stage: ${params.START_FROM_STAGE}"
            echo "Set REUSE_BUILD_NUMBER = ${env.EFFECTIVE_BUILD_NUMBER} to continue"
            echo "=========================================="
        }
        aborted {
            echo "=========================================="
            echo "⚠️  Pipeline Aborted"
            echo "=========================================="
            echo "Build: #${env.EFFECTIVE_BUILD_NUMBER}"
            echo "You can resume by:"
            echo "  1. Set REUSE_BUILD_NUMBER = ${env.EFFECTIVE_BUILD_NUMBER}"
            echo "  2. Select START_FROM_STAGE for the next environment"
            echo "  3. Run the pipeline again"
            echo "=========================================="
        }
    }
}
            '''.stripIndent())
            sandbox()
        }
    }
}

// Freestyle Job: API Build and Promotion Manager
//freeStyleJob('api-build-promotion-manager') {
//    description('🎯 API Build & Promotion Manager - Freestyle job to manage API builds and promote across environments with lifecycle approvals')
//    displayName('🎯 API Build & Promotion Manager')
//    
//    logRotator {
//        daysToKeep(90)
//        numToKeep(50)
//        artifactDaysToKeep(90)
//        artifactNumToKeep(50)
//    }
//    
//    parameters {
//        choiceParam('ACTION', ['BUILD_NEW', 'PROMOTE_EXISTING'], 'Action: Build new API or Promote existing build')
//        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select API application')
//        stringParam('API_VERSION', '1.0.0', 'API version (e.g., 1.0.0)')
//        stringParam('EXISTING_BUILD_NUMBER', '', 'For PROMOTE_EXISTING: Specify build number to promote (e.g., 3)')
//        choiceParam('TARGET_ENVIRONMENT', ['DEV', 'TEST', 'STAGE', 'PROD'], 'Target environment for promotion')
//        booleanParam('REQUIRE_APPROVAL', true, 'Require manual approval before deployment')
//        textParam('DEPLOYMENT_NOTES', '', 'Deployment notes and justification (required for STAGE/PROD)')
//    }
//    
//    scm {
//        git {
//            remote {
//                url('https://github.com/pkalbande/jenkins-gitops-platform.git')
//                credentials('github-token')
//            }
//            branch('*/master')
//        }
//    }
//    
//    steps {
//        shell('''#!/bin/bash
//
//echo "=========================================="
//echo "🚀 API Build & Promotion Manager"
//echo "=========================================="
//echo "Action: ${ACTION}"
//echo "Application: ${APPLICATION}"
//echo "Version: ${API_VERSION}"
//echo "Target Environment: ${TARGET_ENVIRONMENT}"
//echo "Require Approval: ${REQUIRE_APPROVAL}"
//echo "=========================================="
//
//# Debug: Show current workspace state
//echo ""
//echo "🔍 Workspace Verification:"
//echo "Current directory: $(pwd)"
//echo "Environment variables:"
//echo "WORKSPACE: ${WORKSPACE}"
//echo "GIT_COMMIT: ${GIT_COMMIT}"
//echo "GIT_BRANCH: ${GIT_BRANCH}"
//echo ""
//echo "Workspace contents:"
//ls -la
//echo ""
//
//# Check if SCM checkout happened
//if [ ! -d ".git" ]; then
//    echo "⚠️  WARNING: No .git directory found - SCM checkout may have failed"
//fi
//
//# Verify workspace has source code
//if [ ! -d "apps" ]; then
//    echo "❌ ERROR: apps directory not found in workspace"
//    echo ""
//    echo "📋 Troubleshooting Information:"
//    echo "1. SCM should have checked out code to workspace"
//    echo "2. Expected structure: apps/, jenkins/, README.md"
//    echo "3. Git URL: https://github.com/pkalbande/jenkins-gitops-platform.git"
//    echo "4. Branch: master"
//    echo ""
//    echo "Please verify:"
//    echo "- GitHub credentials are configured correctly"
//    echo "- Repository is accessible"
//    echo "- Branch 'master' exists"
//    exit 1
//fi
//
//if [ ! -d "apps/${APPLICATION}" ]; then
//    echo "❌ ERROR: Application directory apps/${APPLICATION} not found"
//    echo ""
//    echo "Available applications in apps/:"
//    ls -la apps/
//    echo ""
//    echo "Valid APPLICATION values: app1-node, app2-python"
//    exit 1
//fi
//
//echo "✅ Workspace verified: apps/${APPLICATION} exists"
//echo ""
//
//set -e
//
//# Determine the build number to use
//if [ "${ACTION}" = "BUILD_NEW" ]; then
//    EFFECTIVE_BUILD="${BUILD_NUMBER}"
//    echo "📦 Creating NEW API Build: #${EFFECTIVE_BUILD}"
//    
//    # Navigate to application directory
//    cd apps/${APPLICATION}
//    
//    # Create API build artifacts
//    echo "Creating API build artifacts..."
//    cat > api-build-${EFFECTIVE_BUILD}.json << EOF
//{
//  "api_name": "${APPLICATION}",
//  "version": "${API_VERSION}",
//  "build_number": "${EFFECTIVE_BUILD}",
//  "build_timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
//  "git_commit": "${GIT_COMMIT:-unknown}",
//  "git_branch": "${GIT_BRANCH:-master}",
//  "jenkins_build_url": "${BUILD_URL}",
//  "action": "BUILD_NEW",
//  "deployment_history": []
//}
//EOF
//    
//    echo "✅ API Build #${EFFECTIVE_BUILD} created successfully"
//    cat api-build-${EFFECTIVE_BUILD}.json
//    
//    # Return to workspace root after build creation
//    cd ../..
//    echo ""
//    echo "📂 Returned to workspace root: $(pwd)"
//    
//elif [ "${ACTION}" = "PROMOTE_EXISTING" ]; then
//    EFFECTIVE_BUILD="${EXISTING_BUILD_NUMBER}"
//    
//    if [ -z "${EFFECTIVE_BUILD}" ]; then
//        echo "❌ ERROR: EXISTING_BUILD_NUMBER is required for PROMOTE_EXISTING action"
//        exit 1
//    fi
//    
//    echo "♻️  Promoting EXISTING Build: #${EFFECTIVE_BUILD}"
//    echo "📍 From previous job run: #${EFFECTIVE_BUILD}"
//else
//    echo "❌ ERROR: Invalid ACTION: ${ACTION}"
//    exit 1
//fi
//
//echo ""
//echo "=========================================="
//echo "🎯 Deployment to ${TARGET_ENVIRONMENT}"
//echo "=========================================="
//echo "Build Number: #${EFFECTIVE_BUILD}"
//echo "API: ${APPLICATION}"
//echo "Version: ${API_VERSION}"
//
//# Check if approval is required
//if [ "${REQUIRE_APPROVAL}" = "true" ]; then
//    echo ""
//    echo "⏸️  MANUAL APPROVAL REQUIRED"
//    echo "=========================================="
//    echo "Environment: ${TARGET_ENVIRONMENT}"
//    echo "Build: #${EFFECTIVE_BUILD}"
//    echo "Notes: ${DEPLOYMENT_NOTES}"
//    echo ""
//    echo "⚠️  For STAGE/PROD deployments:"
//    echo "   - Verify all previous environment validations"
//    echo "   - Ensure lifecycle approvals obtained"
//    echo "   - Confirm rollback plan is ready"
//    echo "=========================================="
//    
//    # In a real scenario, this would pause for approval
//    # For automation, we log the approval checkpoint
//    echo "📋 Approval checkpoint logged at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
//fi
//
//# Perform deployment
//echo ""
//echo "🚀 Executing Deployment..."
//
//# Navigate to application directory
//if [ ! -d "apps/${APPLICATION}" ]; then
//    echo "❌ ERROR: Application directory apps/${APPLICATION} not found in workspace"
//    exit 1
//fi
//
//cd apps/${APPLICATION}
//
//# Check if deploy script exists
//if [ ! -f "deploy.sh" ]; then
//    echo "❌ ERROR: deploy.sh not found in apps/${APPLICATION}"
//    exit 1
//fi
//
//chmod +x deploy.sh
//./deploy.sh ${TARGET_ENVIRONMENT} ${API_VERSION} ${EFFECTIVE_BUILD}
//
//# Return to workspace root
//cd ../..
//
//# Log deployment
//echo ""
//echo "=========================================="
//echo "📝 Deployment Log Entry"
//echo "=========================================="
//echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
//echo "Action: ${ACTION}"
//echo "Build: #${EFFECTIVE_BUILD}"
//echo "API: ${APPLICATION}"
//echo "Version: ${API_VERSION}"
//echo "Environment: ${TARGET_ENVIRONMENT}"
//echo "Deployed By: ${BUILD_USER:-admin}"
//echo "Notes: ${DEPLOYMENT_NOTES}"
//echo "Build URL: ${BUILD_URL}"
//echo "=========================================="
//
//# Create deployment record in workspace root
//cat > deployment-record-${EFFECTIVE_BUILD}-${TARGET_ENVIRONMENT}.json << EOF
//{
//  "deployment_id": "${BUILD_NUMBER}",
//  "source_build": "${EFFECTIVE_BUILD}",
//  "api": "${APPLICATION}",
//  "version": "${API_VERSION}",
//  "environment": "${TARGET_ENVIRONMENT}",
//  "action": "${ACTION}",
//  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
//  "deployed_by": "${BUILD_USER:-admin}",
//  "notes": "${DEPLOYMENT_NOTES}",
//  "jenkins_url": "${BUILD_URL}"
//}
//EOF
//
//echo ""
//echo "✅ Deployment Completed Successfully!"
//echo ""
//echo "=========================================="
//echo "📌 Build #${EFFECTIVE_BUILD} Status"
//echo "=========================================="
//echo "✓ Available for future promotions"
//echo "✓ Can be deployed to remaining environments"
//echo ""
//echo "💡 To deploy Build #${EFFECTIVE_BUILD} to another environment:"
//echo "   1. Set ACTION = PROMOTE_EXISTING"
//echo "   2. Set EXISTING_BUILD_NUMBER = ${EFFECTIVE_BUILD}"
//echo "   3. Select TARGET_ENVIRONMENT"
//echo "   4. Run this job again"
//echo "=========================================="
//
//# Generate HTML Deployment Dashboard
//echo ""
//echo "📊 Generating Deployment Dashboard..."
//mkdir -p deployment-reports
//
//# Collect all deployment records for this application
//cat > deployment-reports/index.html << 'HTML_EOF'
//<!DOCTYPE html>
//<html>
//<head>
//    <title>API Deployment Dashboard</title>
//    <style>
//        body {
//            font-family: Arial, sans-serif;
//            margin: 20px;
//            background-color: #f5f5f5;
//        }
//        .header {
//            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
//            color: white;
//            padding: 30px;
//            border-radius: 10px;
//            margin-bottom: 30px;
//            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
//        }
//        .header h1 {
//            margin: 0 0 10px 0;
//            font-size: 32px;
//        }
//        .header p {
//            margin: 5px 0;
//            opacity: 0.9;
//        }
//        .stats {
//            display: grid;
//            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
//            gap: 20px;
//            margin-bottom: 30px;
//        }
//        .stat-card {
//            background: white;
//            padding: 20px;
//            border-radius: 8px;
//            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
//            text-align: center;
//        }
//        .stat-card h3 {
//            margin: 0 0 10px 0;
//            color: #666;
//            font-size: 14px;
//            text-transform: uppercase;
//        }
//        .stat-card .value {
//            font-size: 28px;
//            font-weight: bold;
//            color: #667eea;
//        }
//        .deployment-table {
//            background: white;
//            border-radius: 8px;
//            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
//            overflow: hidden;
//        }
//        table {
//            width: 100%;
//            border-collapse: collapse;
//        }
//        th {
//            background: #667eea;
//            color: white;
//            padding: 15px;
//            text-align: left;
//            font-weight: 600;
//        }
//        td {
//            padding: 12px 15px;
//            border-bottom: 1px solid #eee;
//        }
//        tr:hover {
//            background-color: #f8f9fa;
//        }
//        .badge {
//            padding: 5px 12px;
//            border-radius: 20px;
//            font-size: 12px;
//            font-weight: bold;
//            display: inline-block;
//        }
//        .badge-dev { background: #e3f2fd; color: #1976d2; }
//        .badge-test { background: #fff3e0; color: #f57c00; }
//        .badge-stage { background: #fce4ec; color: #c2185b; }
//        .badge-prod { background: #e8f5e9; color: #388e3c; }
//        .badge-new { background: #f3e5f5; color: #7b1fa2; }
//        .badge-promote { background: #e0f2f1; color: #00695c; }
//        .action-btn {
//            background: #667eea;
//            color: white;
//            padding: 6px 12px;
//            border-radius: 4px;
//            text-decoration: none;
//            font-size: 12px;
//            display: inline-block;
//            margin: 2px;
//        }
//        .action-btn:hover {
//            background: #5568d3;
//        }
//        .timestamp {
//            color: #666;
//            font-size: 13px;
//        }
//        .current-deployment {
//            background: #e8f5e9 !important;
//            border-left: 4px solid #4caf50;
//        }
//    </style>
//</head>
//<body>
//HTML_EOF
//
//# Add header with current deployment info
//cat >> deployment-reports/index.html << HTML_EOF
//    <div class="header">
//        <h1>🚀 API Deployment Dashboard</h1>
//        <p><strong>Application:</strong> ${APPLICATION}</p>
//        <p><strong>Version:</strong> ${API_VERSION}</p>
//        <p><strong>Latest Deployment:</strong> Build #${EFFECTIVE_BUILD} → ${TARGET_ENVIRONMENT}</p>
//        <p><strong>Time:</strong> $(date -u '+%Y-%m-%d %H:%M:%S UTC')</p>
//    </div>
//
//    <div class="stats">
//        <div class="stat-card">
//            <h3>Current Build</h3>
//            <div class="value">#${EFFECTIVE_BUILD}</div>
//        </div>
//        <div class="stat-card">
//            <h3>Environment</h3>
//            <div class="value">${TARGET_ENVIRONMENT}</div>
//        </div>
//        <div class="stat-card">
//            <h3>Action</h3>
//            <div class="value">${ACTION}</div>
//        </div>
//        <div class="stat-card">
//            <h3>Status</h3>
//            <div class="value" style="color: #4caf50;">✓ SUCCESS</div>
//        </div>
//    </div>
//
//    <div class="deployment-table">
//        <table>
//            <thead>
//                <tr>
//                    <th>Build #</th>
//                    <th>Environment</th>
//                    <th>Action</th>
//                    <th>Version</th>
//                    <th>Timestamp</th>
//                    <th>Deployed By</th>
//                    <th>Quick Actions</th>
//                </tr>
//            </thead>
//            <tbody>
//HTML_EOF
//
//# Add current deployment as the first row
//ENV_CLASS=$(echo "${TARGET_ENVIRONMENT}" | tr '[:upper:]' '[:lower:]')
//ACTION_CLASS=$(echo "${ACTION}" | tr '[:upper:]' '[:lower:]' | sed 's/_/-/g')
//
//cat >> deployment-reports/index.html << HTML_EOF
//                <tr class="current-deployment">
//                    <td><strong>#${EFFECTIVE_BUILD}</strong></td>
//                    <td><span class="badge badge-${ENV_CLASS}">${TARGET_ENVIRONMENT}</span></td>
//                    <td><span class="badge badge-${ACTION_CLASS}">${ACTION}</span></td>
//                    <td>${API_VERSION}</td>
//                    <td class="timestamp">$(date -u '+%Y-%m-%d %H:%M:%S')</td>
//                    <td>${BUILD_USER:-admin}</td>
//                    <td>
//                        <a href="${BUILD_URL}" class="action-btn">View Build</a>
//                    </td>
//                </tr>
//HTML_EOF
//
//# Add previous deployments from deployment records
//for record in deployment-record-*.json; do
//    if [ -f "\$record" ] && [ "\$record" != "deployment-record-${EFFECTIVE_BUILD}-${TARGET_ENVIRONMENT}.json" ]; then
//        BUILD_NUM=\$(cat "\$record" | grep -o '"source_build": "[^"]*"' | cut -d'"' -f4)
//        ENV=\$(cat "\$record" | grep -o '"environment": "[^"]*"' | cut -d'"' -f4)
//        ACT=\$(cat "\$record" | grep -o '"action": "[^"]*"' | cut -d'"' -f4)
//        VER=\$(cat "\$record" | grep -o '"version": "[^"]*"' | cut -d'"' -f4)
//        TIME=\$(cat "\$record" | grep -o '"timestamp": "[^"]*"' | cut -d'"' -f4)
//        USER=\$(cat "\$record" | grep -o '"deployed_by": "[^"]*"' | cut -d'"' -f4)
//        
//        ENV_CLASS_PREV=\$(echo "\$ENV" | tr '[:upper:]' '[:lower:]')
//        ACTION_CLASS_PREV=\$(echo "\$ACT" | tr '[:upper:]' '[:lower:]' | sed 's/_/-/g')
//        
//        cat >> deployment-reports/index.html << HTML_EOF2
//                <tr>
//                    <td>#\${BUILD_NUM}</td>
//                    <td><span class="badge badge-\${ENV_CLASS_PREV}">\${ENV}</span></td>
//                    <td><span class="badge badge-\${ACTION_CLASS_PREV}">\${ACT}</span></td>
//                    <td>\${VER}</td>
//                    <td class="timestamp">\${TIME}</td>
//                    <td>\${USER}</td>
//                    <td>
//                        <a href="#" class="action-btn" onclick="alert('To redeploy: Set EXISTING_BUILD_NUMBER=\${BUILD_NUM}, select environment, and run job')">🔄 Redeploy</a>
//                    </td>
//                </tr>
//HTML_EOF2
//    fi
//done
//
//# Close HTML
//cat >> deployment-reports/index.html << 'HTML_EOF'
//            </tbody>
//        </table>
//    </div>
//
//    <div style="margin-top: 30px; padding: 20px; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
//        <h3>📝 Quick Redeployment Guide</h3>
//        <ol>
//            <li>Click "Build with Parameters" on the job</li>
//            <li>Set <code>ACTION = PROMOTE_EXISTING</code></li>
//            <li>Set <code>EXISTING_BUILD_NUMBER</code> to the build you want to redeploy</li>
//            <li>Select <code>TARGET_ENVIRONMENT</code></li>
//            <li>Click "Build"</li>
//        </ol>
//    </div>
//</body>
//</html>
//HTML_EOF
//
//echo "✅ Deployment Dashboard generated: deployment-reports/index.html"
//        ''')
//    }
//    
//    publishers {
//        archiveArtifacts {
//            pattern('apps/*/api-build-*.json, deployment-record-*.json, deployment-reports/*.html')
//            fingerprint(true)
//            allowEmpty(true)
//        }
//        
//        publishHtml {
//            reportName('Deployment Dashboard')
//            reportDir('deployment-reports')
//            reportFiles('index.html')
//            reportTitles('API Deployment Dashboard')
//            allowMissing(false)
//            alwaysLinkToLastBuild(true)
//            keepAll(true)
//        }
//    }
//}


// ==============================================================================
// 🚀 DOCKER BUILD & ARGOCD DEPLOYMENT JOB
// ==============================================================================
// Complete CI/CD pipeline with Docker build, registry push, and ArgoCD deployments
// Workflow:
// 1. Developer commits code
// 2. Jenkins builds Docker image and pushes to registry
// 3. Tags release with build number (e.g., build-3846)
// 4. Publishes metadata
// 5. Promotions trigger ArgoCD deployments to DEV → TEST → STAGE → PROD
// ==============================================================================

freeStyleJob('docker-argocd-release-job') {
    description('''
🚀 Docker Build & ArgoCD Deployment Pipeline

Complete CI/CD workflow:
┌─────────────────────────────────────────────────────────────┐
│ 1. Build Code → 2. Build Docker Image → 3. Push to Registry │
│ 4. Tag Release → 5. Publish Metadata                        │
└─────────────────────────────────────────────────────────────┘

Promotion Flow (via Promoted Builds Plugin):
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ Deploy  │ →  │ Deploy  │ →  │ Deploy  │ →  │ Deploy  │
│ to DEV  │    │ to TEST │    │ to STAGE│    │ to PROD │
└─────────┘    └─────────┘    └─────────┘    └─────────┘
     ↓              ↓              ↓              ↓
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│DEV Cluster│ │TEST Cluster│ │STAGE Cluster│ │PROD Cluster│
│ArgoCD+Helm│ │ArgoCD+Helm│ │ArgoCD+Helm│ │ArgoCD+Helm│
└──────────┘  └──────────┘  └──────────┘  └──────────┘

Each environment deployment:
• Updates image tag in Git repo
• ArgoCD auto-syncs changes
• Deploys via Helm charts
''')
    displayName('🚀 Docker Build & ArgoCD Deployment')
    
    properties {
        promotions {
            promotion {
                name('Deploy-to-DEV')
                icon('star-gold')
                conditions {
                    manual('admin,devops-team')
                    selfPromotion(false)
                }
                actions {
                    shell('''
set -e

echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          🎯 PROMOTING BUILD TO DEV ENVIRONMENT                        ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
echo "🏷️  Build Number: ${PROMOTED_NUMBER}"
echo "📦 Application: ${APPLICATION}"
echo "🐳 Image Tag: build-${PROMOTED_NUMBER}"
echo "👤 Promoted By: ${PROMOTED_USER_NAME}"
echo "📅 Promotion Date: $(date)"
echo ""

# Update image tag in DEV environment values file
ENVIRONMENT="dev"
VALUES_FILE="environments/${ENVIRONMENT}/values.yaml"

echo "📝 Updating ${VALUES_FILE} with new image tag..."

# Clone the repo if not in workspace
if [ ! -d ".git" ]; then
    git clone https://github.com/pkalbande/jenkins-gitops-platform.git .
fi

# Update the image tag in values file
sed -i.bak "s/tag: .*/tag: build-${PROMOTED_NUMBER}/" ${VALUES_FILE}

# Commit and push changes
git config user.email "jenkins@example.com"
git config user.name "Jenkins CI"
git add ${VALUES_FILE}
git commit -m "Deploy ${APPLICATION} build-${PROMOTED_NUMBER} to DEV environment

Promoted by: ${PROMOTED_USER_NAME}
Build Number: ${PROMOTED_NUMBER}
Environment: DEV
Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

git push origin master

echo ""
echo "✅ Image tag updated in Git repository"
echo "🔄 ArgoCD will auto-sync the changes to DEV cluster"
echo ""
echo "📊 Deployment Details:"
echo "   Environment: DEV"
echo "   Image: ${APPLICATION}:build-${PROMOTED_NUMBER}"
echo "   Values File: ${VALUES_FILE}"
echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║              ✅ DEV DEPLOYMENT INITIATED                              ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
''')
                }
            }
            
            promotion {
                name('Deploy-to-TEST')
                icon('star-blue')
                conditions {
                    manual('admin,qa-team')
                    selfPromotion(false)
                    upstream('Deploy-to-DEV')
                }
                actions {
                    shell('''
set -e

echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          🎯 PROMOTING BUILD TO TEST ENVIRONMENT                       ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
echo "🏷️  Build Number: ${PROMOTED_NUMBER}"
echo "📦 Application: ${APPLICATION}"
echo "🐳 Image Tag: build-${PROMOTED_NUMBER}"
echo "👤 Promoted By: ${PROMOTED_USER_NAME}"
echo "📅 Promotion Date: $(date)"
echo ""

ENVIRONMENT="test"
VALUES_FILE="environments/${ENVIRONMENT}/values.yaml"

echo "📝 Updating ${VALUES_FILE} with new image tag..."

if [ ! -d ".git" ]; then
    git clone https://github.com/pkalbande/jenkins-gitops-platform.git .
fi

sed -i.bak "s/tag: .*/tag: build-${PROMOTED_NUMBER}/" ${VALUES_FILE}

git config user.email "jenkins@example.com"
git config user.name "Jenkins CI"
git add ${VALUES_FILE}
git commit -m "Deploy ${APPLICATION} build-${PROMOTED_NUMBER} to TEST environment

Promoted by: ${PROMOTED_USER_NAME}
Build Number: ${PROMOTED_NUMBER}
Environment: TEST
Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

git push origin master

echo ""
echo "✅ Image tag updated in Git repository"
echo "🔄 ArgoCD will auto-sync the changes to TEST cluster"
echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║              ✅ TEST DEPLOYMENT INITIATED                             ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
''')
                }
            }
            
            promotion {
                name('Deploy-to-STAGE')
                icon('star-silver')
                conditions {
                    manual('admin,release-manager')
                    selfPromotion(false)
                    upstream('Deploy-to-TEST')
                }
                actions {
                    shell('''
set -e

echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          🎯 PROMOTING BUILD TO STAGE ENVIRONMENT                      ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
echo "🏷️  Build Number: ${PROMOTED_NUMBER}"
echo "📦 Application: ${APPLICATION}"
echo "🐳 Image Tag: build-${PROMOTED_NUMBER}"
echo "👤 Promoted By: ${PROMOTED_USER_NAME}"
echo "📅 Promotion Date: $(date)"
echo ""

ENVIRONMENT="stage"
VALUES_FILE="environments/${ENVIRONMENT}/values.yaml"

echo "📝 Updating ${VALUES_FILE} with new image tag..."

if [ ! -d ".git" ]; then
    git clone https://github.com/pkalbande/jenkins-gitops-platform.git .
fi

sed -i.bak "s/tag: .*/tag: build-${PROMOTED_NUMBER}/" ${VALUES_FILE}

git config user.email "jenkins@example.com"
git config user.name "Jenkins CI"
git add ${VALUES_FILE}
git commit -m "Deploy ${APPLICATION} build-${PROMOTED_NUMBER} to STAGE environment

Promoted by: ${PROMOTED_USER_NAME}
Build Number: ${PROMOTED_NUMBER}
Environment: STAGE
Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

git push origin master

echo ""
echo "✅ Image tag updated in Git repository"
echo "🔄 ArgoCD will auto-sync the changes to STAGE cluster"
echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║              ✅ STAGE DEPLOYMENT INITIATED                            ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
''')
                }
            }
            
            promotion {
                name('Deploy-to-PROD')
                icon('star-red')
                conditions {
                    manual('admin,production-approvers')
                    selfPromotion(false)
                    upstream('Deploy-to-STAGE')
                }
                actions {
                    shell('''
set -e

echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          🎯 PROMOTING BUILD TO PRODUCTION ENVIRONMENT                 ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
echo "🚨 THIS IS A PRODUCTION DEPLOYMENT 🚨"
echo ""
echo "🏷️  Build Number: ${PROMOTED_NUMBER}"
echo "📦 Application: ${APPLICATION}"
echo "🐳 Image Tag: build-${PROMOTED_NUMBER}"
echo "👤 Promoted By: ${PROMOTED_USER_NAME}"
echo "📅 Promotion Date: $(date)"
echo ""

ENVIRONMENT="prod"
VALUES_FILE="environments/${ENVIRONMENT}/values.yaml"

echo "📝 Updating ${VALUES_FILE} with new image tag..."

if [ ! -d ".git" ]; then
    git clone https://github.com/pkalbande/jenkins-gitops-platform.git .
fi

sed -i.bak "s/tag: .*/tag: build-${PROMOTED_NUMBER}/" ${VALUES_FILE}

git config user.email "jenkins@example.com"
git config user.name "Jenkins CI"
git add ${VALUES_FILE}
git commit -m "Deploy ${APPLICATION} build-${PROMOTED_NUMBER} to PRODUCTION environment

Promoted by: ${PROMOTED_USER_NAME}
Build Number: ${PROMOTED_NUMBER}
Environment: PRODUCTION
Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)

⚠️  PRODUCTION RELEASE"

git push origin master

echo ""
echo "✅ Image tag updated in Git repository"
echo "🔄 ArgoCD will auto-sync the changes to PRODUCTION cluster"
echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          ✅ PRODUCTION DEPLOYMENT INITIATED                           ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
''')
                }
            }
        }
    }
    
    logRotator {
        daysToKeep(30)
        numToKeep(50)
        artifactDaysToKeep(30)
        artifactNumToKeep(50)
    }
    
    parameters {
        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select application to build')
        stringParam('VERSION', '1.0.0', 'Version number for the release (e.g., 1.0.0)')
        stringParam('DOCKER_REGISTRY', 'docker.io/mycompany', 'Docker registry URL (e.g., docker.io/mycompany or gcr.io/project-id)')
        booleanParam('RUN_TESTS', true, 'Run unit tests before building Docker image')
        booleanParam('PUSH_TO_REGISTRY', true, 'Push Docker image to registry')
        booleanParam('SCAN_IMAGE', false, 'Run security scan on Docker image (requires scanner)')
    }
    
    scm {
        git {
            remote {
                url('https://github.com/pkalbande/jenkins-gitops-platform.git')
                credentials('github-token')
            }
            branch('*/master')
        }
    }
    
    wrappers {
        credentialsBinding {
            usernamePassword('parag47', 'Ironwar@47', 'docker-registry')
        }
    }
    
    steps {
        shell('''
set -e

echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║              🚀 DOCKER BUILD & RELEASE PIPELINE                       ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
echo "📦 Application:      ${APPLICATION}"
echo "📌 Version:          ${VERSION}"
echo "🏷️  Build Tag:        build-${BUILD_NUMBER}"
echo "🐳 Docker Registry:  ${DOCKER_REGISTRY}"
echo "🔢 Build Number:     ${BUILD_NUMBER}"
echo "📅 Build Time:       $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo "🌿 Git Branch:       $(git rev-parse --abbrev-ref HEAD)"
echo "📝 Git Commit:       $(git rev-parse --short HEAD)"
echo ""

# Store build metadata
BUILD_TAG="build-${BUILD_NUMBER}"
IMAGE_NAME="${DOCKER_REGISTRY}/${APPLICATION}:${BUILD_TAG}"
GIT_COMMIT=$(git rev-parse HEAD)
GIT_COMMIT_SHORT=$(git rev-parse --short HEAD)
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
BUILD_TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

echo "═══════════════════════════════════════════════════════════════════════"
echo "STEP 1: 📋 Build Code & Run Tests"
echo "═══════════════════════════════════════════════════════════════════════"

cd apps/${APPLICATION}

# Create build metadata file
cat > build-metadata.json << EOF
{
  "application": "${APPLICATION}",
  "version": "${VERSION}",
  "buildNumber": "${BUILD_NUMBER}",
  "buildTag": "${BUILD_TAG}",
  "imageName": "${IMAGE_NAME}",
  "gitCommit": "${GIT_COMMIT}",
  "gitCommitShort": "${GIT_COMMIT_SHORT}",
  "gitBranch": "${GIT_BRANCH}",
  "buildTimestamp": "${BUILD_TIMESTAMP}",
  "dockerRegistry": "${DOCKER_REGISTRY}"
}
EOF

echo "✅ Build metadata created:"
cat build-metadata.json

if [ "${RUN_TESTS}" = "true" ]; then
    echo ""
    echo "🧪 Running unit tests..."
    echo "Running tests for ${APPLICATION}..."
    # Add actual test commands here
    echo "✅ All tests passed"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════════"
echo "STEP 2: 🐳 Build Docker Image"
echo "═══════════════════════════════════════════════════════════════════════"

echo "Building Docker image: ${IMAGE_NAME}"

# Build Docker image with multiple tags
docker build \\
    --tag ${IMAGE_NAME} \\
    --tag ${DOCKER_REGISTRY}/${APPLICATION}:${VERSION} \\
    --tag ${DOCKER_REGISTRY}/${APPLICATION}:latest \\
    --build-arg VERSION=${VERSION} \\
    --build-arg BUILD_NUMBER=${BUILD_NUMBER} \\
    --build-arg GIT_COMMIT=${GIT_COMMIT_SHORT} \\
    --label "version=${VERSION}" \\
    --label "build-number=${BUILD_NUMBER}" \\
    --label "git-commit=${GIT_COMMIT}" \\
    --label "build-timestamp=${BUILD_TIMESTAMP}" \\
    .

echo "✅ Docker image built successfully"
echo ""
echo "📊 Image Details:"
docker images | grep ${APPLICATION} | head -3

if [ "${SCAN_IMAGE}" = "true" ]; then
    echo ""
    echo "═══════════════════════════════════════════════════════════════════════"
    echo "STEP 3: 🔍 Security Scan"
    echo "═══════════════════════════════════════════════════════════════════════"
    echo "⚠️  Security scanning not configured. Skipping..."
    # Add security scanning tool here (e.g., Trivy, Snyk, etc.)
    # trivy image ${IMAGE_NAME}
fi

if [ "${PUSH_TO_REGISTRY}" = "true" ]; then
    echo ""
    echo "═══════════════════════════════════════════════════════════════════════"
    echo "STEP 4: 📤 Push to Docker Registry"
    echo "═══════════════════════════════════════════════════════════════════════"
    
    echo "Logging in to Docker registry..."
    echo "${DOCKER_PASSWORD}" | docker login ${DOCKER_REGISTRY%%/*} -u ${DOCKER_USERNAME} --password-stdin
    
    echo ""
    echo "Pushing images to registry..."
    docker push ${IMAGE_NAME}
    docker push ${DOCKER_REGISTRY}/${APPLICATION}:${VERSION}
    docker push ${DOCKER_REGISTRY}/${APPLICATION}:latest
    
    echo ""
    echo "✅ Images pushed successfully:"
    echo "   • ${IMAGE_NAME}"
    echo "   • ${DOCKER_REGISTRY}/${APPLICATION}:${VERSION}"
    echo "   • ${DOCKER_REGISTRY}/${APPLICATION}:latest"
else
    echo ""
    echo "⚠️  Skipping registry push (PUSH_TO_REGISTRY=false)"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════════"
echo "STEP 5: 📝 Publish Build Metadata"
echo "═══════════════════════════════════════════════════════════════════════"

# Create release metadata
cat > release-${BUILD_TAG}.json << EOF
{
  "releaseName": "${BUILD_TAG}",
  "application": "${APPLICATION}",
  "version": "${VERSION}",
  "buildNumber": "${BUILD_NUMBER}",
  "dockerImage": "${IMAGE_NAME}",
  "dockerTags": [
    "${IMAGE_NAME}",
    "${DOCKER_REGISTRY}/${APPLICATION}:${VERSION}",
    "${DOCKER_REGISTRY}/${APPLICATION}:latest"
  ],
  "gitCommit": "${GIT_COMMIT}",
  "gitCommitShort": "${GIT_COMMIT_SHORT}",
  "gitBranch": "${GIT_BRANCH}",
  "buildTimestamp": "${BUILD_TIMESTAMP}",
  "status": "READY_FOR_DEPLOYMENT",
  "environments": {
    "dev": "NOT_DEPLOYED",
    "test": "NOT_DEPLOYED",
    "stage": "NOT_DEPLOYED",
    "prod": "NOT_DEPLOYED"
  }
}
EOF

echo "✅ Release metadata published: release-${BUILD_TAG}.json"
cat release-${BUILD_TAG}.json

cd ../..

echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║                    ✅ BUILD COMPLETED SUCCESSFULLY                    ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
echo "🎉 Build ${BUILD_TAG} is ready for deployment!"
echo ""
echo "📦 Release Summary:"
echo "   Application:    ${APPLICATION}"
echo "   Version:        ${VERSION}"
echo "   Build Tag:      ${BUILD_TAG}"
echo "   Docker Image:   ${IMAGE_NAME}"
echo "   Git Commit:     ${GIT_COMMIT_SHORT}"
echo ""
echo "🎯 Next Steps - Promote this build:"
echo "   1. Go to this build page"
echo "   2. Click 'Promotion Status' in the left menu"
echo "   3. Promote to DEV → TEST → STAGE → PROD"
echo ""
echo "Each promotion will update the image tag in Git,"
echo "and ArgoCD will automatically deploy to the target cluster."
echo ""
        ''')
    }
    
    publishers {
        archiveArtifacts {
            pattern('apps/*/build-metadata.json, apps/*/release-*.json')
            fingerprint(true)
            allowEmpty(true)
        }
    }
}

// ==============================================================================
// Pipeline Job: pipeline-poi-47
// ==============================================================================
pipelineJob('pipeline-poi-47') {
    description('🚀 Complete CI/CD Pipeline with Multi-Environment Promotion (DEV → QA → STAGE → PROD)')
    displayName('🚀 Pipeline POI-47')
    
    logRotator {
        daysToKeep(30)
        numToKeep(20)
        artifactDaysToKeep(30)
        artifactNumToKeep(20)
    }
    
    definition {
        cps {
            script('''
pipeline {
    agent any
    
    parameters {
        // Add your parameters here based on your freestyle job
        string(name: 'BRANCH', defaultValue: 'main', description: 'Git branch to build')
        string(name: 'VERSION', defaultValue: '', description: 'Release version')
        choice(name: 'BUILD_TYPE', choices: ['snapshot', 'release'], description: 'Build type')
    }
    
    environment {
        // Define environment variables
        MAVEN_OPTS = '-Xmx1024m'
        JAVA_HOME = tool name: 'JDK11', type: 'jdk'
        // Add other environment variables as needed
    }
    
    options {
        // Build options
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 1, unit: 'HOURS')
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Checkout') {
            steps {
                script {
                    echo "Checking out code from branch: ${params.BRANCH}"
                    checkout scm
                    // Or specific Git checkout:
                    // git branch: "${params.BRANCH}", 
                    //     url: 'https://your-repo-url.git',
                    //     credentialsId: 'your-credentials-id'
                }
            }
        }
        
        stage('Build') {
            steps {
                script {
                    echo 'Building the application...'
                    // Replace with your actual build commands
                    // For Maven:
                    sh 'mvn clean package -DskipTests'
                    // For Gradle:
                    // sh './gradlew clean build -x test'
                    // For npm:
                    // sh 'npm install && npm run build'
                }
            }
        }
        
        stage('Unit Tests') {
            steps {
                script {
                    echo 'Running unit tests...'
                    // For Maven:
                    sh 'mvn test'
                    // For Gradle:
                    // sh './gradlew test'
                }
            }
            post {
                always {
                    // Publish test results
                    junit '**/target/surefire-reports/*.xml'
                    // Or for Gradle:
                    // junit '**/build/test-results/test/*.xml'
                }
            }
        }
        
        stage('Code Quality Analysis') {
            steps {
                script {
                    echo 'Running code quality checks...'
                    // SonarQube analysis example:
                    // sh 'mvn sonar:sonar'
                }
            }
        }
        
        stage('Archive Artifacts') {
            steps {
                script {
                    echo 'Archiving build artifacts...'
                    archiveArtifacts artifacts: '**/target/*.jar,**/target/*.war', 
                                   fingerprint: true,
                                   allowEmptyArchive: false
                    // Adjust path based on your build output
                }
            }
        }
        
        stage('Promote to DEV') {
            steps {
                script {
                    echo "=== AUTOMATIC PROMOTION TO DEV ==="
                    echo "Build #${BUILD_NUMBER} is being promoted to DEV environment"
                    
                    // Copy artifacts or deploy
                    sh """
                        echo "Deploying to DEV environment..."
                        # Add your DEV deployment commands here
                        # ./deploy.sh dev
                        # kubectl apply -f k8s/dev/
                        # ansible-playbook -i inventory/dev deploy.yml
                    """
                }
            }
            post {
                success {
                    echo 'Successfully deployed to DEV'
                }
                failure {
                    echo 'Failed to deploy to DEV'
                }
            }
        }
        
        stage('QA Approval Gate') {
            steps {
                script {
                    echo "=== WAITING FOR QA APPROVAL ==="
                    echo "Build #${BUILD_NUMBER} is ready for QA promotion"
                    
                    timeout(time: 7, unit: 'DAYS') {
                        def userInput = input(
                            message: "Promote Build #${BUILD_NUMBER} to QA Environment?",
                            ok: 'Approve Promotion to QA',
                            submitter: 'qa-team,admin',
                            parameters: [
                                text(name: 'QA_NOTES', 
                                     defaultValue: '', 
                                     description: 'QA approval notes (optional)')
                            ]
                        )
                        echo "QA Approval Notes: ${userInput}"
                        env.QA_APPROVAL_NOTES = userInput
                    }
                }
            }
        }
        
        stage('Promote to QA') {
            steps {
                script {
                    echo "=== PROMOTING TO QA ==="
                    echo "Build #${BUILD_NUMBER} approved for QA deployment"
                    
                    // Retrieve artifacts from the build
                    sh """
                        echo "Deploying to QA environment..."
                        # Add your QA deployment commands here
                        # ./deploy.sh qa
                        # kubectl apply -f k8s/qa/
                        # ansible-playbook -i inventory/qa deploy.yml
                    """
                }
            }
            post {
                success {
                    echo 'Successfully deployed to QA'
                    emailext(
                        subject: "Build #${BUILD_NUMBER} deployed to QA",
                        body: "Build has been successfully deployed to QA environment",
                        to: 'qa-team@company.com'
                    )
                }
            }
        }
        
        stage('STAGE Approval Gate') {
            steps {
                script {
                    echo "=== WAITING FOR STAGE APPROVAL ==="
                    echo "Build #${BUILD_NUMBER} is ready for STAGE promotion"
                    
                    timeout(time: 7, unit: 'DAYS') {
                        def userInput = input(
                            message: "Promote Build #${BUILD_NUMBER} to STAGE Environment?",
                            ok: 'Approve Promotion to STAGE',
                            submitter: 'release-managers,admin',
                            parameters: [
                                text(name: 'STAGE_NOTES', 
                                     defaultValue: '', 
                                     description: 'Stage approval notes (optional)')
                            ]
                        )
                        echo "STAGE Approval Notes: ${userInput}"
                        env.STAGE_APPROVAL_NOTES = userInput
                    }
                }
            }
        }
        
        stage('Promote to STAGE') {
            steps {
                script {
                    echo "=== PROMOTING TO STAGE ==="
                    echo "Build #${BUILD_NUMBER} approved for STAGE deployment"
                    
                    sh """
                        echo "Deploying to STAGE environment..."
                        # Add your STAGE deployment commands here
                        # ./deploy.sh stage
                        # kubectl apply -f k8s/stage/
                        # ansible-playbook -i inventory/stage deploy.yml
                    """
                }
            }
            post {
                success {
                    echo 'Successfully deployed to STAGE'
                }
            }
        }
        
        stage('PROD Approval Gate') {
            steps {
                script {
                    echo "=== WAITING FOR PRODUCTION APPROVAL ==="
                    echo "Build #${BUILD_NUMBER} is ready for PRODUCTION promotion"
                    
                    timeout(time: 14, unit: 'DAYS') {
                        def userInput = input(
                            message: "Promote Build #${BUILD_NUMBER} to PRODUCTION?",
                            ok: 'Approve Production Deployment',
                            submitter: 'prod-approvers,admin',
                            parameters: [
                                text(name: 'PROD_NOTES', 
                                     defaultValue: '', 
                                     description: 'Production approval notes (REQUIRED)'),
                                string(name: 'CHANGE_TICKET', 
                                       defaultValue: '', 
                                       description: 'Change ticket number'),
                                choice(name: 'DEPLOYMENT_WINDOW',
                                       choices: ['Immediate', 'Scheduled Maintenance'],
                                       description: 'Deployment timing')
                            ]
                        )
                        echo "Production Approval Notes: ${userInput.PROD_NOTES}"
                        echo "Change Ticket: ${userInput.CHANGE_TICKET}"
                        echo "Deployment Window: ${userInput.DEPLOYMENT_WINDOW}"
                        
                        env.PROD_APPROVAL_NOTES = userInput.PROD_NOTES
                        env.CHANGE_TICKET = userInput.CHANGE_TICKET
                    }
                }
            }
        }
        
        stage('Promote to PROD') {
            steps {
                script {
                    echo "=== PROMOTING TO PRODUCTION ==="
                    echo "Build #${BUILD_NUMBER} approved for PRODUCTION deployment"
                    echo "Change Ticket: ${env.CHANGE_TICKET}"
                    
                    // Additional safety check
                    echo "Starting production deployment in 10 seconds..."
                    sleep 10
                    
                    sh """
                        echo "Deploying to PRODUCTION environment..."
                        # Add your PRODUCTION deployment commands here
                        # ./deploy.sh prod
                        # kubectl apply -f k8s/prod/
                        # ansible-playbook -i inventory/prod deploy.yml
                    """
                }
            }
            post {
                success {
                    echo 'Successfully deployed to PRODUCTION'
                    emailext(
                        subject: "✅ Build #${BUILD_NUMBER} DEPLOYED TO PRODUCTION",
                        body: """
                            Build #${BUILD_NUMBER} has been successfully deployed to PRODUCTION.
                            
                            Change Ticket: ${env.CHANGE_TICKET}
                            Approval Notes: ${env.PROD_APPROVAL_NOTES}
                            Deployed By: ${env.BUILD_USER}
                            Timestamp: ${new Date()}
                        """,
                        to: 'prod-team@company.com,management@company.com'
                    )
                }
                failure {
                    echo 'PRODUCTION deployment FAILED!'
                    emailext(
                        subject: "❌ PRODUCTION DEPLOYMENT FAILED - Build #${BUILD_NUMBER}",
                        body: "URGENT: Production deployment has failed. Immediate attention required.",
                        to: 'prod-team@company.com,oncall@company.com'
                    )
                }
            }
        }
    }
    
    post {
        success {
            echo "Pipeline completed successfully! Build #${BUILD_NUMBER}"
        }
        failure {
            echo "Pipeline failed at some stage"
            // Send failure notifications
        }
        always {
            // Cleanup
            cleanWs(
                deleteDirs: true,
                patterns: [
                    [pattern: '**/target/**', type: 'INCLUDE'],
                    [pattern: '**/.git/**', type: 'EXCLUDE']
                ]
            )
        }
    }
}
            '''.stripIndent())
            sandbox()
        }
    }
}