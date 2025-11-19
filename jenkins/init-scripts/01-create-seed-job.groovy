import jenkins.model.Jenkins

def jenkins = Jenkins.instance
def jobName = 'seed-job'

// Check if job already exists
def existingJob = jenkins.getItem(jobName)
if (existingJob != null) {
    println "✅ Seed job '${jobName}' already exists, skipping creation"
    return
}

println "🌱 Creating seed job '${jobName}' using XML configuration..."

// Create job from XML
def xml = '''<?xml version='1.1' encoding='UTF-8'?>
<project>
  <description>Seed job that creates other Jenkins jobs from DSL scripts in Git</description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <scm class="hudson.plugins.git.GitSCM">
    <configVersion>2</configVersion>
    <userRemoteConfigs>
      <hudson.plugins.git.UserRemoteConfig>
        <url>https://github.com/pkalbande/jenkins-gitops-platform.git</url>
        <credentialsId>github-token</credentialsId>
      </hudson.plugins.git.UserRemoteConfig>
    </userRemoteConfigs>
    <branches>
      <hudson.plugins.git.BranchSpec>
        <name>*/master</name>
      </hudson.plugins.git.BranchSpec>
    </branches>
  </scm>
  <canRoam>true</canRoam>
  <disabled>false</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers/>
  <concurrentBuild>false</concurrentBuild>
  <builders>
    <javaposse.jobdsl.plugin.ExecuteDslScripts>
      <targets>jenkins/dsl/jobs.groovy</targets>
      <usingScriptText>false</usingScriptText>
      <sandbox>false</sandbox>
      <removedJobAction>IGNORE</removedJobAction>
      <removedViewAction>IGNORE</removedViewAction>
      <removedConfigFilesAction>IGNORE</removedConfigFilesAction>
      <lookupStrategy>JENKINS_ROOT</lookupStrategy>
    </javaposse.jobdsl.plugin.ExecuteDslScripts>
  </builders>
  <publishers/>
  <buildWrappers/>
</project>'''

// Create job from XML
def xmlStream = new ByteArrayInputStream(xml.getBytes())
jenkins.createProjectFromXML(jobName, xmlStream)

println "✅ Seed job '${jobName}' created successfully!"
println "🎯 Jenkins will now execute the DSL script to create other jobs"
