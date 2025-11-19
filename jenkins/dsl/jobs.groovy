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

# Run deployment script
cd apps/${APPLICATION}
chmod +x deploy.sh
./deploy.sh DEV ${RELEASE_VERSION} ${PROMOTED_NUMBER}
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

# Run deployment script
cd apps/${APPLICATION}
chmod +x deploy.sh
./deploy.sh QA ${RELEASE_VERSION} ${PROMOTED_NUMBER}
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

# Run deployment script
cd apps/${APPLICATION}
chmod +x deploy.sh
./deploy.sh STAGE ${RELEASE_VERSION} ${PROMOTED_NUMBER}
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

# Run deployment script
cd apps/${APPLICATION}
chmod +x deploy.sh
./deploy.sh PROD ${RELEASE_VERSION} ${PROMOTED_NUMBER}
                        ''')
                    }
                }
            }
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
