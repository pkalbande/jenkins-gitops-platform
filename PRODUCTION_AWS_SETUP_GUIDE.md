# Production Jenkins GitOps Setup Guide - AWS with High Availability

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [High Availability Design](#high-availability-design)
3. [Prerequisites](#prerequisites)
4. [AWS Infrastructure Setup](#aws-infrastructure-setup)
5. [Jenkins Master High Availability](#jenkins-master-high-availability)
6. [Production Deployment Steps](#production-deployment-steps)
7. [Disaster Recovery](#disaster-recovery)
8. [Monitoring & Maintenance](#monitoring--maintenance)
9. [Security Best Practices](#security-best-practices)

---

## Architecture Overview

### Production Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AWS Cloud (Production)                          │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                        VPC (10.0.0.0/16)                            │    │
│  │                                                                      │    │
│  │  ┌─────────────────────┐              ┌─────────────────────┐     │    │
│  │  │   Public Subnet     │              │   Public Subnet     │     │    │
│  │  │   AZ-1 (us-east-1a) │              │   AZ-2 (us-east-1b) │     │    │
│  │  │                     │              │                     │     │    │
│  │  │  ┌──────────────┐   │              │  ┌──────────────┐   │     │    │
│  │  │  │   NAT GW     │   │              │  │   NAT GW     │   │     │    │
│  │  │  └──────────────┘   │              │  └──────────────┘   │     │    │
│  │  │  ┌──────────────┐   │              │  ┌──────────────┐   │     │    │
│  │  │  │   ALB (443)  │◄──┼──────────────┼──┤   ALB (443)  │   │     │    │
│  │  │  └──────────────┘   │              │  └──────────────┘   │     │    │
│  │  └─────────────────────┘              └─────────────────────┘     │    │
│  │           │                                     │                  │    │
│  │  ┌────────▼──────────────┐              ┌──────▼────────────────┐ │    │
│  │  │   Private Subnet      │              │   Private Subnet      │ │    │
│  │  │   AZ-1 (us-east-1a)   │              │   AZ-2 (us-east-1b)   │ │    │
│  │  │                       │              │                       │ │    │
│  │  │  ┌─────────────────┐  │              │  ┌─────────────────┐  │ │    │
│  │  │  │  EKS Node Group │  │              │  │  EKS Node Group │  │ │    │
│  │  │  │  (3 nodes min)  │  │              │  │  (3 nodes min)  │  │ │    │
│  │  │  └─────────────────┘  │              │  └─────────────────┘  │ │    │
│  │  │                       │              │                       │ │    │
│  │  │  ┌─────────────────┐  │              │  ┌─────────────────┐  │ │    │
│  │  │  │ Jenkins Master  │  │              │  │ Jenkins Master  │  │ │    │
│  │  │  │    (Active)     │◄─┼──────────────┼─►│   (Passive)     │  │ │    │
│  │  │  │   StatefulSet   │  │              │  │   StatefulSet   │  │ │    │
│  │  │  └────────┬────────┘  │              │  └────────┬────────┘  │ │    │
│  │  │           │            │              │           │            │ │    │
│  │  │           │            │              │           │            │ │    │
│  │  │  ┌────────▼────────┐  │              │  ┌────────▼────────┐  │ │    │
│  │  │  │  EFS (Shared)   │◄─┼──────────────┼─►│  EFS (Shared)   │  │ │    │
│  │  │  │  Jenkins Home   │  │              │  │  Jenkins Home   │  │ │    │
│  │  │  └─────────────────┘  │              │  └─────────────────┘  │ │    │
│  │  │                       │              │                       │ │    │
│  │  │  ┌─────────────────┐  │              │  ┌─────────────────┐  │ │    │
│  │  │  │ Dynamic Agents  │  │              │  │ Dynamic Agents  │  │ │    │
│  │  │  │   (Kubernetes)  │  │              │  │   (Kubernetes)  │  │ │    │
│  │  │  └─────────────────┘  │              │  └─────────────────┘  │ │    │
│  │  │                       │              │                       │ │    │
│  │  │  ┌─────────────────┐  │              │  ┌─────────────────┐  │ │    │
│  │  │  │   ArgoCD        │  │              │  │   ArgoCD        │  │ │    │
│  │  │  │   (HA Mode)     │  │              │  │   (HA Mode)     │  │ │    │
│  │  │  └─────────────────┘  │              │  └─────────────────┘  │ │    │
│  │  └───────────────────────┘              └───────────────────────┘ │    │
│  │                                                                    │    │
│  │  ┌──────────────────────────────────────────────────────────────┐ │    │
│  │  │                     Data Tier (Multi-AZ)                      │ │    │
│  │  │                                                                │ │    │
│  │  │  ┌──────────────┐        ┌──────────────┐                    │ │    │
│  │  │  │   RDS PG     │        │  ElastiCache │                    │ │    │
│  │  │  │  (Multi-AZ)  │        │    (Redis)   │                    │ │    │
│  │  │  │   (Optional) │        │   (Optional) │                    │ │    │
│  │  │  └──────────────┘        └──────────────┘                    │ │    │
│  │  └──────────────────────────────────────────────────────────────┘ │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                    Supporting Services                            │   │
│  │                                                                    │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │   │
│  │  │   ECR    │  │    S3    │  │  Route53 │  │   KMS    │        │   │
│  │  │(Container│  │ (Backup/ │  │   (DNS)  │  │(Secrets) │        │   │
│  │  │ Registry)│  │Artifacts)│  │          │  │          │        │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘        │   │
│  │                                                                    │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │   │
│  │  │CloudWatch│  │   X-Ray  │  │  Secrets │  │   IAM    │        │   │
│  │  │  (Logs/  │  │ (Tracing)│  │ Manager  │  │  (RBAC)  │        │   │
│  │  │ Metrics) │  │          │  │          │  │          │        │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘        │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## High Availability Design

### Jenkins Master Active-Passive Setup

```
┌────────────────────────────────────────────────────────────────────────┐
│                    Jenkins HA Architecture                             │
│                                                                         │
│  ┌──────────────────────┐                  ┌──────────────────────┐   │
│  │   Availability Zone  │                  │  Availability Zone   │   │
│  │        us-east-1a    │                  │     us-east-1b       │   │
│  │                      │                  │                      │   │
│  │  ┌────────────────┐  │                  │  ┌────────────────┐  │   │
│  │  │ Jenkins Master │  │                  │  │ Jenkins Master │  │   │
│  │  │    (ACTIVE)    │  │   Failover      │  │   (PASSIVE)    │  │   │
│  │  │                │  │ ◄────────────►  │  │                │  │   │
│  │  │ StatefulSet    │  │   Heartbeat     │  │ StatefulSet    │  │   │
│  │  │ Replica: 1     │  │                  │  │ Replica: 0     │  │   │
│  │  └────────┬───────┘  │                  │  └────────┬───────┘  │   │
│  │           │          │                  │           │          │   │
│  │           │          │                  │           │          │   │
│  │  ┌────────▼───────┐  │   ┌──────────┐  │  ┌────────▼───────┐  │   │
│  │  │  EBS Volume    │  │   │   EFS    │  │  │  EBS Volume    │  │   │
│  │  │  (Local Cache) │  │   │ (Shared) │  │  │  (Local Cache) │  │   │
│  │  └────────────────┘  │   │ Storage  │  │  └────────────────┘  │   │
│  │                      │   │          │  │                      │   │
│  │           │          │   │  - Jobs  │  │           │          │   │
│  │           └──────────┼──►│  - Build │◄─┼───────────┘          │   │
│  │                      │   │  - Config│  │                      │   │
│  │                      │   │  - Logs  │  │                      │   │
│  │                      │   └──────────┘  │                      │   │
│  └──────────────────────┘                  └──────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │                     Health Check & Failover Logic                 │ │
│  │                                                                    │ │
│  │  1. Kubernetes Liveness Probe monitors Active master             │ │
│  │  2. On failure: Active → 0 replicas, Passive → 1 replica         │ │
│  │  3. Passive pod mounts same EFS volume                           │ │
│  │  4. Passive becomes Active (< 2 min recovery time)               │ │
│  │  5. DNS/Service endpoint automatically routes to new Active      │ │
│  └──────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### Failover Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      Failover Sequence                          │
└─────────────────────────────────────────────────────────────────┘

    Normal Operation                    Failure Detected
    ────────────────                    ────────────────
    
    ┌─────────────┐                     ┌─────────────┐
    │   Active    │                     │   Active    │
    │   Master    │ ◄─── Health ───X    │   Master    │
    │  (Running)  │      Check          │  (Failed)   │
    └──────┬──────┘                     └──────┬──────┘
           │                                    │
           │ EFS Mount                          │ EFS Unmount
           ▼                                    ▼
    ┌─────────────┐                     ┌─────────────┐
    │     EFS     │                     │     EFS     │
    │Jenkins_Home │                     │Jenkins_Home │
    └─────────────┘                     └─────────────┘
    
    ┌─────────────┐                     ┌─────────────┐
    │  Passive    │                     │  Passive    │
    │   Master    │                     │   Master    │
    │ (Waiting)   │                     │ (Waiting)   │
    └─────────────┘                     └─────────────┘


    Failover Initiated                  Recovery Complete
    ──────────────────                  ─────────────────
    
    ┌─────────────┐                     ┌─────────────┐
    │   Active    │                     │   Active    │
    │   Master    │                     │   Master    │
    │ (Scaled 0)  │                     │(Terminated) │
    └─────────────┘                     └─────────────┘
    
    ┌─────────────┐                     ┌─────────────┐
    │     EFS     │                     │     EFS     │
    │Jenkins_Home │◄────────┐           │Jenkins_Home │
    └─────────────┘         │           └──────┬──────┘
                            │                   │
                     EFS Mount                  │ Mounted
                            │                   ▼
    ┌─────────────┐         │           ┌─────────────┐
    │  Passive    │         │           │    NEW      │
    │   Master    │─────────┘           │   Active    │
    │(Scaling up) │                     │   Master    │
    └─────────────┘                     │  (Running)  │
                                        └──────┬──────┘
                                               │
        Time: ~1-2 minutes                     │ Service Traffic
                                               ▼
                                        ┌─────────────┐
                                        │   Users/    │
                                        │   Jobs      │
                                        └─────────────┘
                                        
                                        ✓ Zero Data Loss
                                        ✓ All Jobs Preserved
                                        ✓ Build History Intact
```

---

## Prerequisites

### AWS Account Setup
- AWS Account with appropriate permissions
- AWS CLI configured (`aws configure`)
- Terraform >= 1.5.0 (for Infrastructure as Code)
- kubectl >= 1.28
- helm >= 3.12
- eksctl >= 0.150.0

### Domain & SSL
- Registered domain (e.g., jenkins.yourcompany.com)
- AWS Certificate Manager (ACM) certificate

### Tools Installation
```bash
# Install AWS CLI
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Install eksctl
curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin

# Install Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Install ArgoCD CLI
curl -sSL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
sudo install -m 555 argocd-linux-amd64 /usr/local/bin/argocd
```

---

## AWS Infrastructure Setup

### Step 1: Create EKS Cluster

Create `eks-cluster.yaml`:

```yaml
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: jenkins-gitops-prod
  region: us-east-1
  version: "1.28"

vpc:
  cidr: 10.0.0.0/16
  clusterEndpoints:
    publicAccess: true
    privateAccess: true

iam:
  withOIDC: true
  serviceAccounts:
  - metadata:
      name: jenkins-sa
      namespace: jenkins-prod
    wellKnownPolicies:
      ebsCSIController: true
      efsCSIController: true
    attachPolicyARNs:
    - arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
    - arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy
    - arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

availabilityZones: ["us-east-1a", "us-east-1b", "us-east-1c"]

managedNodeGroups:
  - name: jenkins-ng-spot
    instanceTypes: 
      - t3.large
      - t3a.large
    spot: true
    minSize: 3
    maxSize: 10
    desiredCapacity: 3
    volumeSize: 100
    volumeType: gp3
    labels:
      role: worker
      workload: jenkins
    tags:
      nodegroup-role: worker
      environment: production
    iam:
      withAddonPolicies:
        ebs: true
        efs: true
        albIngress: true
        cloudWatch: true

  - name: jenkins-ng-ondemand
    instanceTypes: ["t3.xlarge"]
    minSize: 2
    maxSize: 6
    desiredCapacity: 2
    volumeSize: 100
    volumeType: gp3
    labels:
      role: jenkins-master
      workload: jenkins-master
    taints:
      - key: jenkins-master
        value: "true"
        effect: NoSchedule
    tags:
      nodegroup-role: jenkins-master
      environment: production

addons:
  - name: vpc-cni
    version: latest
  - name: coredns
    version: latest
  - name: kube-proxy
    version: latest
  - name: aws-ebs-csi-driver
    version: latest
  - name: aws-efs-csi-driver
    version: latest

cloudWatch:
  clusterLogging:
    enableTypes: ["api", "audit", "authenticator", "controllerManager", "scheduler"]
```

Create the cluster:
```bash
eksctl create cluster -f eks-cluster.yaml
```

### Step 2: Create EFS for Shared Storage

```bash
# Get VPC ID
VPC_ID=$(aws eks describe-cluster \
  --name jenkins-gitops-prod \
  --query "cluster.resourcesVpcConfig.vpcId" \
  --output text)

# Get Subnet IDs
SUBNET_IDS=$(aws eks describe-cluster \
  --name jenkins-gitops-prod \
  --query "cluster.resourcesVpcConfig.subnetIds" \
  --output text)

# Create Security Group for EFS
SG_ID=$(aws ec2 create-security-group \
  --group-name jenkins-efs-sg \
  --description "Security group for Jenkins EFS" \
  --vpc-id $VPC_ID \
  --output text --query 'GroupId')

# Allow NFS traffic
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID \
  --protocol tcp \
  --port 2049 \
  --cidr 10.0.0.0/16

# Create EFS File System
EFS_ID=$(aws efs create-file-system \
  --performance-mode generalPurpose \
  --throughput-mode elastic \
  --encrypted \
  --tags Key=Name,Value=jenkins-home-prod \
  --output text --query 'FileSystemId')

echo "EFS File System ID: $EFS_ID"

# Create Mount Targets in each AZ
for SUBNET in $SUBNET_IDS; do
  aws efs create-mount-target \
    --file-system-id $EFS_ID \
    --subnet-id $SUBNET \
    --security-groups $SG_ID
done

# Wait for EFS to be available
aws efs describe-file-systems --file-system-id $EFS_ID
```

### Step 3: Install EFS CSI Driver

```bash
# Create IAM policy for EFS CSI Driver
cat > efs-csi-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "elasticfilesystem:DescribeAccessPoints",
        "elasticfilesystem:DescribeFileSystems",
        "elasticfilesystem:DescribeMountTargets",
        "ec2:DescribeAvailabilityZones"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "elasticfilesystem:CreateAccessPoint"
      ],
      "Resource": "*",
      "Condition": {
        "StringLike": {
          "aws:RequestTag/efs.csi.aws.com/cluster": "true"
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": "elasticfilesystem:DeleteAccessPoint",
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "aws:ResourceTag/efs.csi.aws.com/cluster": "true"
        }
      }
    }
  ]
}
EOF

aws iam create-policy \
  --policy-name AmazonEKS_EFS_CSI_Driver_Policy \
  --policy-document file://efs-csi-policy.json

# Install EFS CSI Driver
kubectl apply -k "github.com/kubernetes-sigs/aws-efs-csi-driver/deploy/kubernetes/overlays/stable/?ref=release-1.7"
```

---

## Jenkins Master High Availability

### Step 4: Create Jenkins HA Configuration

Create `jenkins-ha-values-prod.yaml`:

```yaml
jenkins:
  name: jenkins
  namespace: jenkins-prod
  
  # High Availability Configuration
  master:
    # Active-Passive: Only one replica runs at a time
    replicas: 1
    
    # Use StatefulSet for stable network identity
    deploymentType: StatefulSet
    
    # Node affinity to spread across AZs
    affinity:
      podAntiAffinity:
        requiredDuringSchedulingIgnoredDuringExecution:
        - labelSelector:
            matchExpressions:
            - key: app
              operator: In
              values:
              - jenkins-master
          topologyKey: topology.kubernetes.io/zone
      nodeAffinity:
        requiredDuringSchedulingIgnoredDuringExecution:
          nodeSelectorTerms:
          - matchExpressions:
            - key: role
              operator: In
              values:
              - jenkins-master
    
    # Tolerations for dedicated nodes
    tolerations:
    - key: jenkins-master
      operator: Equal
      value: "true"
      effect: NoSchedule
    
  image: jenkins/jenkins
  tag: lts-jdk17
  imagePullPolicy: IfNotPresent
  
  serviceAccount: jenkins-sa
  
  javaOpts: >-
    -Xmx4096m
    -Xms2048m
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=100
    -XX:+UseStringDeduplication
    -Djenkins.install.runSetupWizard=false
    -Dhudson.slaves.NodeProvisioner.initialDelay=0
    -Dhudson.slaves.NodeProvisioner.MARGIN=50
    -Dhudson.slaves.NodeProvisioner.MARGIN0=0.85
  
  # Enhanced resource limits for production
  resources:
    requests:
      cpu: 2000m
      memory: 4Gi
    limits:
      cpu: 4000m
      memory: 8Gi
  
  # Service Configuration
  service:
    type: ClusterIP
    port: 8080
    annotations:
      service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
      service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
  
  # EFS Persistent Storage
  persistence:
    enabled: true
    storageClass: efs-sc
    accessMode: ReadWriteMany
    size: 100Gi
    annotations:
      volume.beta.kubernetes.io/storage-class: "efs-sc"
  
  # Health checks for HA failover
  healthChecks:
    livenessProbe:
      httpGet:
        path: /login
        port: 8080
      initialDelaySeconds: 180
      periodSeconds: 30
      timeoutSeconds: 10
      failureThreshold: 5
      successThreshold: 1
    
    readinessProbe:
      httpGet:
        path: /login
        port: 8080
      initialDelaySeconds: 120
      periodSeconds: 15
      timeoutSeconds: 10
      failureThreshold: 3
      successThreshold: 1
  
  # Ingress with ALB
  ingress:
    enabled: true
    annotations:
      kubernetes.io/ingress.class: alb
      alb.ingress.kubernetes.io/scheme: internet-facing
      alb.ingress.kubernetes.io/target-type: ip
      alb.ingress.kubernetes.io/listen-ports: '[{"HTTP": 80}, {"HTTPS": 443}]'
      alb.ingress.kubernetes.io/ssl-redirect: '443'
      alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:ACCOUNT_ID:certificate/CERT_ID
      alb.ingress.kubernetes.io/healthcheck-path: /login
      alb.ingress.kubernetes.io/healthcheck-interval-seconds: '15'
      alb.ingress.kubernetes.io/healthcheck-timeout-seconds: '5'
      alb.ingress.kubernetes.io/success-codes: '200'
      alb.ingress.kubernetes.io/backend-protocol: HTTP
    hosts:
      - host: jenkins.yourcompany.com
        paths:
          - path: /
            pathType: Prefix
    tls:
      - hosts:
        - jenkins.yourcompany.com
  
  # Jenkins Configuration as Code (JCasC)
  casc:
    config: |
      jenkins:
        systemMessage: "🏢 PRODUCTION JENKINS | AWS EKS | High Availability"
        numExecutors: 0
        mode: EXCLUSIVE
        
        securityRealm:
          local:
            allowsSignup: false
            users:
              - id: admin
                password: ${JENKINS_ADMIN_PASSWORD}
        
        authorizationStrategy:
          roleBased:
            roles:
              global:
                - name: "admin"
                  description: "Jenkins administrators"
                  permissions:
                    - "Overall/Administer"
                  assignments:
                    - "admin"
                - name: "developer"
                  description: "Developers"
                  permissions:
                    - "Overall/Read"
                    - "Job/Read"
                    - "Job/Build"
                    - "Job/Cancel"
                  assignments:
                    - "authenticated"
        
        clouds:
          - kubernetes:
              name: kubernetes
              serverUrl: https://kubernetes.default
              namespace: jenkins-prod
              jenkinsUrl: http://jenkins:8080
              jenkinsTunnel: jenkins:50000
              containerCapStr: 50
              maxRequestsPerHostStr: 64
              retentionTimeout: 5
              connectTimeout: 10
              readTimeout: 20
              templates:
                - name: jnlp-agent
                  namespace: jenkins-prod
                  label: jnlp-agent
                  nodeUsageMode: NORMAL
                  idleMinutes: 10
                  containers:
                    - name: jnlp
                      image: jenkins/inbound-agent:latest
                      workingDir: /home/jenkins/agent
                      ttyEnabled: true
                      resourceRequestCpu: 500m
                      resourceRequestMemory: 1Gi
                      resourceLimitCpu: 2000m
                      resourceLimitMemory: 2Gi
                  volumes:
                    - emptyDirVolume:
                        memory: false
                        mountPath: /tmp
      
      jobs:
        - script: >
            pipelineJob('release-build-job') {
              description('Production Release Build Job')
              displayName('Release Build Job')
              properties {
                buildDiscarder {
                  strategy {
                    logRotator {
                      numToKeepStr('50')
                      artifactNumToKeepStr('50')
                    }
                  }
                }
              }
              parameters {
                choice(name: 'APPLICATION', choices: ['app1-node', 'app2-python'], description: 'Application')
                string(name: 'VERSION', defaultValue: '1.0.0', description: 'Version')
                booleanParam(name: 'DEPLOY_TO_DEV', defaultValue: false, description: 'Deploy to dev')
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
        
        - script: >
            pipelineJob('promotion-orchestrator-job') {
              description('Production Promotion Orchestrator')
              displayName('Promotion Orchestrator Job')
              parameters {
                choice(name: 'APPLICATION', choices: ['app1-node', 'app2-python'], description: 'Application')
                string(name: 'VERSION', defaultValue: '', description: 'Version')
                choice(name: 'SOURCE_ENV', choices: ['dev', 'test', 'stage'], description: 'Source')
                choice(name: 'TARGET_ENV', choices: ['test', 'stage', 'prod'], description: 'Target')
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
      
      unclassified:
        location:
          url: https://jenkins.yourcompany.com
        
        globalLibraries:
          libraries:
            - name: shared-library
              retriever:
                modernSCM:
                  scm:
                    git:
                      remote: https://github.com/yourcompany/jenkins-shared-library.git
      
      credentials:
        system:
          domainCredentials:
            - credentials:
                - string:
                    scope: GLOBAL
                    id: github-token
                    secret: ${GITHUB_TOKEN}
                    description: GitHub Token
                - usernamePassword:
                    scope: GLOBAL
                    id: docker-registry
                    username: ${DOCKER_USERNAME}
                    password: ${DOCKER_PASSWORD}
                    description: Docker Registry
                - string:
                    scope: GLOBAL
                    id: argocd-token
                    secret: ${ARGOCD_TOKEN}
                    description: ArgoCD Token
                - awsCredentials:
                    scope: GLOBAL
                    id: aws-credentials
                    accessKey: ${AWS_ACCESS_KEY_ID}
                    secretKey: ${AWS_SECRET_ACCESS_KEY}
                    description: AWS Credentials

# Backup Configuration
backup:
  enabled: true
  schedule: "0 2 * * *"  # Daily at 2 AM
  retention: 30  # Keep 30 days
  s3Bucket: jenkins-backups-prod
  
# Monitoring
monitoring:
  enabled: true
  prometheus:
    enabled: true
  grafana:
    enabled: true
