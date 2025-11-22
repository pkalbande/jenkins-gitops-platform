// Multibranch pipeline for the release workflow Jenkinsfile
multibranchPipelineJob('multibranch-release-pipeline') {
    description('🌿 Release Multibranch Pipeline - Builds once and creates a release job for DEV/TEST/STAGE/PROD deployments')
    displayName('🌿 Release Multibranch Pipeline')

    branchSources {
        git {
            id('multibranch-release-pipeline-git')
            remote('https://github.com/pkalbande/jenkins-gitops-platform.git')
            credentialsId('github-token')
            includes('*')
        }
    }

    orphanedItemStrategy {
        discardOldItems {
            daysToKeep(14)
            numToKeep(20)
        }
    }

    factory {
        workflowBranchProjectFactory {
            scriptPath('jenkins/pipelines/release/Jenkinsfile')
        }
    }
}
