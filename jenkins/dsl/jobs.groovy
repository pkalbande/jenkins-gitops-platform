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

# Build Docker image with version tag
echo "Building Docker image..."
docker build -t ${APPLICATION}:${RELEASE_VERSION} .
docker tag ${APPLICATION}:${RELEASE_VERSION} ${APPLICATION}:latest

# Create release metadata
echo "Creating release metadata..."
cat > release-metadata.json << EOF
{
  "application": "${APPLICATION}",
  "version": "${RELEASE_VERSION}",
  "build_number": "${BUILD_NUMBER}",
  "build_date": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "git_commit": "${GIT_COMMIT:-unknown}",
  "git_branch": "${GIT_BRANCH:-master}",
  "jenkins_url": "${BUILD_URL}"
}
EOF

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
        
        // Configure Promoted Builds Plugin with 4 promotion levels
        configure { project ->
            project / publishers << 'hudson.plugins.promoted__builds.JobPropertyImpl' {
                activeProcessNames {
                    string('PROMOTE_TO_DEV')
                    string('PROMOTE_TO_QA')
                    string('PROMOTE_TO_STAGE')
                    string('PROMOTE_TO_PROD')
                }
            }
            
            def promotions = project / 'properties' / 'hudson.plugins.promoted__builds.JobPropertyImpl'
            
            // Promotion 1: Deploy to DEV
            promotions << 'hudson.plugins.promoted__builds.PromotionProcess' {
                name('PROMOTE_TO_DEV')
                icon('star-gold')
                conditions {
                    'hudson.plugins.promoted__builds.conditions.SelfPromotionCondition' {
                        evenIfUnstable(false)
                    }
                }
                buildSteps {
                    'hudson.tasks.Shell' {
                        command('''#!/bin/bash
echo "=========================================="
echo "🎯 Promoting Build #${PROMOTED_NUMBER} to DEV"
echo "Application: ${APPLICATION}"
echo "Version: ${RELEASE_VERSION}"
echo "=========================================="

# Simulate deployment to DEV
echo "📦 Deploying ${APPLICATION}:${RELEASE_VERSION} to DEV environment..."
echo "✅ Successfully deployed to DEV!"

# Update deployment manifest (simulated)
echo "Updating GitOps manifests for DEV..."
echo "DEV deployment completed at $(date)"
                        ''')
                    }
                }
            }
            
            // Promotion 2: Deploy to QA
            promotions << 'hudson.plugins.promoted__builds.PromotionProcess' {
                name('PROMOTE_TO_QA')
                icon('star-gold')
                conditions {
                    'hudson.plugins.promoted__builds.conditions.UpstreamPromotionCondition' {
                        promotionNames('PROMOTE_TO_DEV')
                    }
                    'hudson.plugins.promoted__builds.conditions.ManualCondition' {
                        users('')
                    }
                }
                buildSteps {
                    'hudson.tasks.Shell' {
                        command('''#!/bin/bash
echo "=========================================="
echo "🎯 Promoting Build #${PROMOTED_NUMBER} to QA"
echo "Application: ${APPLICATION}"
echo "Version: ${RELEASE_VERSION}"
echo "=========================================="

echo "📦 Deploying ${APPLICATION}:${RELEASE_VERSION} to QA environment..."
echo "✅ Successfully deployed to QA!"

echo "QA deployment completed at $(date)"
                        ''')
                    }
                }
            }
            
            // Promotion 3: Deploy to STAGE
            promotions << 'hudson.plugins.promoted__builds.PromotionProcess' {
                name('PROMOTE_TO_STAGE')
                icon('star-gold')
                conditions {
                    'hudson.plugins.promoted__builds.conditions.UpstreamPromotionCondition' {
                        promotionNames('PROMOTE_TO_QA')
                    }
                    'hudson.plugins.promoted__builds.conditions.ManualCondition' {
                        users('')
                    }
                }
                buildSteps {
                    'hudson.tasks.Shell' {
                        command('''#!/bin/bash
echo "=========================================="
echo "🎯 Promoting Build #${PROMOTED_NUMBER} to STAGE"
echo "Application: ${APPLICATION}"
echo "Version: ${RELEASE_VERSION}"
echo "=========================================="

echo "📦 Deploying ${APPLICATION}:${RELEASE_VERSION} to STAGE environment..."
echo "✅ Successfully deployed to STAGE!"

echo "STAGE deployment completed at $(date)"
                        ''')
                    }
                }
            }
            
            // Promotion 4: Deploy to PROD
            promotions << 'hudson.plugins.promoted__builds.PromotionProcess' {
                name('PROMOTE_TO_PROD')
                icon('star-gold')
                conditions {
                    'hudson.plugins.promoted__builds.conditions.UpstreamPromotionCondition' {
                        promotionNames('PROMOTE_TO_STAGE')
                    }
                    'hudson.plugins.promoted__builds.conditions.ManualCondition' {
                        users('')
                    }
                }
                buildSteps {
                    'hudson.tasks.Shell' {
                        command('''#!/bin/bash
echo "=========================================="
echo "🎯 Promoting Build #${PROMOTED_NUMBER} to PROD"
echo "Application: ${APPLICATION}"
echo "Version: ${RELEASE_VERSION}"
echo "=========================================="

echo "📦 Deploying ${APPLICATION}:${RELEASE_VERSION} to PROD environment..."
echo "⚠️  PRODUCTION DEPLOYMENT IN PROGRESS..."
echo "✅ Successfully deployed to PROD!"

echo "PROD deployment completed at $(date)"
                        ''')
                    }
                }
            }
        }
    }
}

pipelineJob('promotion-orchestrator-job') {
    description('🎯 Promotion Orchestrator Job - Select and promote previous builds across environments (DEV → QA → STAGE → PROD)')
    displayName('🎯 Promotion Orchestrator Job')
    
    properties {
        buildDiscarder {
            strategy {
                logRotator {
                    daysToKeepStr('60')
                    numToKeepStr('20')
                    artifactDaysToKeepStr('60')
                    artifactNumToKeepStr('20')
                }
            }
        }
    }
    
    parameters {
        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select application to promote')
        stringParam('BUILD_NUMBER', '', 'Build number from release-build-job to promote')
        stringParam('VERSION', '', 'Version to promote (e.g., 1.0.0)')
        choiceParam('PROMOTION_LEVEL', ['Deploy-to-DEV', 'Deploy-to-QA', 'Deploy-to-STAGE', 'Deploy-to-PROD'], 'Select promotion level')
        textParam('APPROVAL_NOTES', '', 'Approval notes and justification')
    }
    
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url('https://github.com/pkalbande/jenkins-gitops-platform.git')
                        credentials('github-token')
                    }
                    branch('*/master')
                }
            }
            scriptPath('jenkins/pipelines/promotion-orchestrator/Jenkinsfile')
        }
    }
}
