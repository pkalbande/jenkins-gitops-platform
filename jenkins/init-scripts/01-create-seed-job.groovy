import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.plugins.git.BranchSpec
import javaposse.jobdsl.plugin.ExecuteDslScripts
import javaposse.jobdsl.plugin.RemovedJobAction
import javaposse.jobdsl.plugin.LookupStrategy

def jenkins = Jenkins.instance
def jobName = 'seed-job'

// Check if job already exists
def existingJob = jenkins.getItem(jobName)
if (existingJob != null) {
    println "✅ Seed job '${jobName}' already exists, skipping creation"
    return
}

println "🌱 Creating seed job '${jobName}'..."

// Create freestyle project
def seedJob = jenkins.createProject(FreeStyleProject, jobName)
seedJob.setDisplayName('🌱 DSL Seed Job')
seedJob.setDescription('Seed job that creates other Jenkins jobs from DSL scripts in Git')

// Configure Git SCM
def gitUrl = 'https://github.com/pkalbande/jenkins-gitops-platform.git'
def credentialsId = 'github-token'
def branch = '*/master'

def userRemoteConfig = new UserRemoteConfig(gitUrl, '', '', credentialsId)
def branchSpec = new BranchSpec(branch)
def scm = new GitSCM([userRemoteConfig], [branchSpec], null, null, [])
seedJob.setScm(scm)

// Add DSL build step
def dslBuilder = new ExecuteDslScripts()
dslBuilder.setTargets('jenkins/dsl/jobs.groovy')
dslBuilder.setRemovedJobAction(RemovedJobAction.IGNORE)
dslBuilder.setLookupStrategy(LookupStrategy.JENKINS_ROOT)
seedJob.getBuildersList().add(dslBuilder)

// Save job
seedJob.save()

println "✅ Seed job '${jobName}' created successfully!"
println "🎯 To create the other jobs, trigger this seed job manually or via API"
