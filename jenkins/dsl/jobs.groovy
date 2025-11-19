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

// Pipeline Job 1: Release Pipeline with Manual Approvals
pipelineJob('release-pipeline-job') {
    description('🚀 Release Pipeline - Declarative pipeline with manual approval for TEST, STAGE, and PROD deployments')
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
        booleanParam('SKIP_TESTS', false, 'Skip test execution')
    }
    
    definition {
        cps {
            script('''
pipeline {
    agent any
    
    parameters {
        choice(name: 'APPLICATION', choices: ['app1-node', 'app2-python'], description: 'Select application to build')
        string(name: 'RELEASE_VERSION', defaultValue: '1.0.0', description: 'Version number for the release')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip test execution')
    }
    
    environment {
        BUILD_DATE = sh(script: 'date -u +%Y-%m-%dT%H:%M:%SZ', returnStdout: true).trim()
        GIT_COMMIT_HASH = sh(script: 'git rev-parse HEAD || echo "unknown"', returnStdout: true).trim()
        GIT_BRANCH_NAME = sh(script: 'git rev-parse --abbrev-ref HEAD || echo "master"', returnStdout: true).trim()
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
            steps {
                script {
                    echo "=========================================="
                    echo "🚀 Building Release Version: ${params.RELEASE_VERSION}"
                    echo "Application: ${params.APPLICATION}"
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
        
        stage('Deploy to DEV') {
            steps {
                script {
                    echo "=========================================="
                    echo "🎯 Deploying to DEV Environment"
                    echo "=========================================="
                    
                    dir("apps/${params.APPLICATION}") {
                        sh """
                            chmod +x deploy.sh
                            ./deploy.sh DEV ${params.RELEASE_VERSION} ${env.BUILD_NUMBER}
                        """
                    }
                }
            }
        }
        
        stage('Deploy to TEST') {
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  TEST Deployment - Approval Required"
                    echo "=========================================="
                    
                    timeout(time: 24, unit: 'HOURS') {
                        input message: 'Deploy to TEST environment?', 
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
                            ./deploy.sh TEST ${params.RELEASE_VERSION} ${env.BUILD_NUMBER}
                        """
                    }
                }
            }
        }
        
        stage('Deploy to STAGE') {
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  STAGE Deployment - Approval Required"
                    echo "=========================================="
                    
                    timeout(time: 24, unit: 'HOURS') {
                        input message: 'Deploy to STAGE environment?', 
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
                            ./deploy.sh STAGE ${params.RELEASE_VERSION} ${env.BUILD_NUMBER}
                        """
                    }
                }
            }
        }
        
        stage('Deploy to PROD') {
            steps {
                script {
                    echo "=========================================="
                    echo "⏸️  PRODUCTION Deployment - Approval Required"
                    echo "=========================================="
                    
                    timeout(time: 72, unit: 'HOURS') {
                        input message: 'Deploy to PRODUCTION environment?', 
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
                            ./deploy.sh PROD ${params.RELEASE_VERSION} ${env.BUILD_NUMBER}
                        """
                    }
                }
            }
        }
    }
    
    post {
        success {
            echo "=========================================="
            echo "✅ Pipeline completed successfully!"
            echo "Release ${params.RELEASE_VERSION} deployed through all environments"
            echo "=========================================="
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
