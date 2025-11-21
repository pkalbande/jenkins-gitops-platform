// Jenkins DSL script to create Multibranch Pipeline Job
// This creates a multibranch pipeline that automatically discovers branches
// and creates jobs for each branch matching the specified criteria

multibranchPipelineJob('multibranch-pipeline-example') {
    description('''
🌿 Multibranch Pipeline Example

This pipeline automatically discovers and builds branches from the repository.

Branch Behavior:
┌─────────────────────────────────────────────────────────────┐
│ Feature/Bugfix Branches (feature/*, bugfix/*)                │
│   • Build & Test                                            │
│   • Create PR Comments                                      │
│   • Build Docker Image (local)                              │
│   • Create PR Comments                                      │
│   • No deployment                                            │
├─────────────────────────────────────────────────────────────┤
│ Develop Branch (develop)                                     │
│   • Build & Test                                            │
│   • Build & Push Docker Image                               │
│   • Auto-deploy to DEV                                      │
├─────────────────────────────────────────────────────────────┤
│ Main/Master Branch (main, master)                           │
│   • Full CI/CD Pipeline                                     │
│   • Build & Push Docker Image                               │
│   • Manual approval for deployments                         │
└─────────────────────────────────────────────────────────────┘

The pipeline automatically:
• Discovers branches matching the branch source criteria
• Creates a job for each branch
• Removes jobs for deleted branches
• Builds on push events
''')
    displayName('🌿 Multibranch Pipeline Example')
    
    // logRotator is not supported for multibranchPipelineJob
    
    // Branch Sources - generic Git (avoids GitHub-specific plugin requirements)
    branchSources {
        git {
            id('git-multibranch-example')
            remote('https://github.com/pkalbande/jenkins-gitops-platform.git')
            credentialsId('github-token')
            includes('*')
            excludes('')
        }
    }
    
    // Or use Git branch source (alternative to GitHub)
    /*
    branchSources {
        git {
            id('git-multibranch')
            remote('https://github.com/pkalbande/jenkins-gitops-platform.git')
            credentialsId('github-token')
            
            includes('*')
            
            traits {
                gitBranchDiscovery()
                gitTagDiscovery()
                cleanBeforeCheckoutTrait()
                cloneOptionTrait {
                    extension {
                        shallow(true)
                        depth(1)
                    }
                }
            }
        }
    }
    */
    
    // Orphaned item strategy - what to do with branches that no longer exist
    orphanedItemStrategy {
        discardOldItems {
            daysToKeepStr('7')
            numToKeepStr('10')
        }
        defaultOrphanedItemStrategy {
            pruneDeadBranches(true)
            abortBuilds(false)
        }
    }
    
    // Pipeline definition - points to Jenkinsfile
    factory {
        workflowBranchProjectFactory {
            scriptPath('jenkins/pipelines/multibranch/Jenkinsfile')
        }
    }
    
    // Properties
    properties {
        // Pipeline triggers
        pipelineTriggers {
            triggers {
                // Poll SCM periodically
                scm('H/15 * * * *') // Every 15 minutes
            }
        }
        
        // GitHub project property
        githubProjectProperty {
            projectUrlStr('https://github.com/pkalbande/jenkins-gitops-platform')
        }
        
        // Disable concurrent builds per branch
        disableConcurrentBuildsJobProperty()
    }
    
    // Build configuration
    configure { project ->
        // Additional configuration if needed
        project / 'factory' / 'scriptPath' << 'jenkins/pipelines/multibranch/Jenkinsfile'
    }
}

// Alternative: Multibranch Pipeline for specific application
multibranchPipelineJob('multibranch-app1-node') {
    description('🌿 Multibranch Pipeline for app1-node - Automatically builds all branches')
    displayName('🌿 Multibranch: app1-node')
    
    branchSources {
        git {
            id('git-app1')
            remote('https://github.com/pkalbande/jenkins-gitops-platform.git')
            credentialsId('github-token')
            includes('*')
            excludes('')
        }
    }
    
    factory {
        workflowBranchProjectFactory {
            scriptPath('jenkins/pipelines/multibranch/Jenkinsfile')
        }
    }
    
    properties {
        pipelineTriggers {
            triggers {
                scm('H/15 * * * *')
            }
        }
    }
    
    // Set environment variable for application
    configure { project ->
        project / 'factory' / 'scriptPath' << 'jenkins/pipelines/multibranch/Jenkinsfile'
        // You can add environment variables here if needed
    }
}

// Alternative: Multibranch Pipeline with Bitbucket
/*
multibranchPipelineJob('multibranch-bitbucket-example') {
    description('🌿 Multibranch Pipeline with Bitbucket')
    displayName('🌿 Multibranch: Bitbucket')
    
    branchSources {
        bitbucket {
            id('bitbucket-multibranch')
            credentialsId('bitbucket-credentials')
            repoOwner('your-workspace')
            repository('your-repo')
            
            traits {
                bitbucketBranchDiscovery()
                bitbucketPullRequestDiscovery()
                bitbucketTagDiscovery()
                cleanBeforeCheckoutTrait()
            }
        }
    }
    
    factory {
        workflowBranchProjectFactory {
            scriptPath('jenkins/pipelines/multibranch/Jenkinsfile')
        }
    }
}
*/

// Alternative: Multibranch Pipeline with GitLab
/*
multibranchPipelineJob('multibranch-gitlab-example') {
    description('🌿 Multibranch Pipeline with GitLab')
    displayName('🌿 Multibranch: GitLab')
    
    branchSources {
        gitlab {
            id('gitlab-multibranch')
            serverName('GitLab')
            credentialsId('gitlab-credentials')
            projectOwner('your-group')
            projectPath('your-project')
            
            traits {
                gitLabBranchDiscovery()
                gitLabPullRequestDiscovery()
                gitLabTagDiscovery()
                cleanBeforeCheckoutTrait()
            }
        }
    }
    
    factory {
        workflowBranchProjectFactory {
            scriptPath('jenkins/pipelines/multibranch/Jenkinsfile')
        }
    }
}
*/

