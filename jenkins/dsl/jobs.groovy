// Jenkins DSL script to create Release Build and Promotion Orchestrator jobs

pipelineJob('release-build-job') {
    description('🚀 Release Build Job - Build code, create versioned release, and archive artifacts using Release Plugin')
    displayName('🚀 Release Build Job')
    
    properties {
        buildDiscarder {
            strategy {
                logRotator {
                    numToKeepStr('10')
                    artifactNumToKeepStr('10')
                }
            }
        }
    }
    
    parameters {
        choiceParam('APPLICATION', ['app1-node', 'app2-python'], 'Select application to build')
        stringParam('VERSION', '1.0.0', 'Version number for the release (e.g., 1.0.0)')
        booleanParam('DEPLOY_TO_DEV', true, 'Automatically deploy to dev environment after build')
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
            scriptPath('jenkins/pipelines/release-build/Jenkinsfile')
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
                    numToKeepStr('20')
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
