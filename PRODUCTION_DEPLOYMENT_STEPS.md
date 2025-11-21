# Production Deployment Steps (Continued)

## Step 5: Create EFS StorageClass

Create `efs-storageclass.yaml`:

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: efs-sc
provisioner: efs.csi.aws.com
parameters:
  provisioningMode: efs-ap
  fileSystemId: fs-XXXXXXXX  # Replace with your EFS ID
  directoryPerms: "700"
  gidRangeStart: "1000"
  gidRangeEnd: "2000"
  basePath: "/jenkins"
mountOptions:
  - tls
  - iam
```

Apply:
```bash
# Replace EFS_ID with your actual EFS ID
sed -i "s/fs-XXXXXXXX/$EFS_ID/" efs-storageclass.yaml
kubectl apply -f efs-storageclass.yaml
```

---

## Step 6: Create Jenkins Namespace and Secrets

```bash
# Create namespace
kubectl create namespace jenkins-prod

# Create secrets
kubectl create secret generic jenkins-secrets \
  --from-literal=JENKINS_ADMIN_PASSWORD='YourSecurePassword123!' \
  --from-literal=GITHUB_TOKEN='ghp_your_github_token' \
  --from-literal=DOCKER_USERNAME='your_docker_username' \
  --from-literal=DOCKER_PASSWORD='your_docker_password' \
  --from-literal=ARGOCD_TOKEN='your_argocd_token' \
  --from-literal=AWS_ACCESS_KEY_ID='your_aws_key' \
  --from-literal=AWS_SECRET_ACCESS_KEY='your_aws_secret' \
  -n jenkins-prod

# Or use AWS Secrets Manager (Recommended for Production)
kubectl create secret generic jenkins-secrets \
  --from-literal=JENKINS_ADMIN_PASSWORD=$(aws secretsmanager get-secret-value \
    --secret-id jenkins/admin-password \
    --query SecretString --output text) \
  -n jenkins-prod
```

---

## Step 7: Install AWS Load Balancer Controller

```bash
# Create IAM policy
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.6.0/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# Create IAM service account
eksctl create iamserviceaccount \
  --cluster=jenkins-gitops-prod \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --role-name AmazonEKSLoadBalancerControllerRole \
  --attach-policy-arn=arn:aws:iam::ACCOUNT_ID:policy/AWSLoadBalancerControllerIAMPolicy \
  --approve

# Install the controller
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=jenkins-gitops-prod \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

---

## Step 8: Deploy ArgoCD in HA Mode

Create `argocd-ha-values.yaml`:

```yaml
global:
  image:
    tag: v2.9.0

redis-ha:
  enabled: true
  haproxy:
    enabled: true
    replicas: 3
  replicas: 3

controller:
  replicas: 2
  resources:
    requests:
      cpu: 500m
      memory: 1Gi
    limits:
      cpu: 2000m
      memory: 2Gi

server:
  replicas: 3
  autoscaling:
    enabled: true
    minReplicas: 2
    maxReplicas: 5
  resources:
    requests:
      cpu: 250m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1Gi
  ingress:
    enabled: true
    annotations:
      kubernetes.io/ingress.class: alb
      alb.ingress.kubernetes.io/scheme: internet-facing
      alb.ingress.kubernetes.io/target-type: ip
      alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS": 443}]'
      alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:ACCOUNT_ID:certificate/CERT_ID
    hosts:
      - argocd.yourcompany.com

repoServer:
  replicas: 3
  autoscaling:
    enabled: true
    minReplicas: 2
    maxReplicas: 5
  resources:
    requests:
      cpu: 250m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1Gi

applicationSet:
  replicas: 2
  resources:
    requests:
      cpu: 100m
      memory: 128Mi
    limits:
      cpu: 500m
      memory: 512Mi
```

Install ArgoCD:
```bash
# Create namespace
kubectl create namespace argocd

# Install ArgoCD with HA
helm repo add argo https://argoproj.github.io/argo-helm
helm install argocd argo/argo-cd \
  -n argocd \
  -f argocd-ha-values.yaml

# Get admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d

# Port-forward to access (or use ingress)
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

---

## Step 9: Deploy Jenkins with HA

Create directory structure:
```bash
mkdir -p production/jenkins
cd production/jenkins
```

Create `jenkins-statefulset.yaml`:

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: jenkins-master
  namespace: jenkins-prod
  labels:
    app: jenkins-master
spec:
  serviceName: jenkins
  replicas: 1
  selector:
    matchLabels:
      app: jenkins-master
  template:
    metadata:
      labels:
        app: jenkins-master
    spec:
      serviceAccountName: jenkins-sa
      securityContext:
        fsGroup: 1000
        runAsUser: 1000
        runAsNonRoot: true
      
      # Anti-affinity to spread across zones
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
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
      
      tolerations:
      - key: jenkins-master
        operator: Equal
        value: "true"
        effect: NoSchedule
      
      containers:
      - name: jenkins
        image: jenkins/jenkins:lts-jdk17
        imagePullPolicy: IfNotPresent
        
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        - name: jnlp
          containerPort: 50000
          protocol: TCP
        
        env:
        - name: JAVA_OPTS
          value: >-
            -Xmx4096m
            -Xms2048m
            -XX:+UseG1GC
            -XX:MaxGCPauseMillis=100
            -Djenkins.install.runSetupWizard=false
        - name: CASC_JENKINS_CONFIG
          value: /var/jenkins_home/casc_configs
        
        envFrom:
        - secretRef:
            name: jenkins-secrets
        
        volumeMounts:
        - name: jenkins-home
          mountPath: /var/jenkins_home
        - name: jenkins-casc-config
          mountPath: /var/jenkins_home/casc_configs
        
        resources:
          requests:
            cpu: 2000m
            memory: 4Gi
          limits:
            cpu: 4000m
            memory: 8Gi
        
        livenessProbe:
          httpGet:
            path: /login
            port: 8080
          initialDelaySeconds: 180
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 5
        
        readinessProbe:
          httpGet:
            path: /login
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 15
          timeoutSeconds: 10
          failureThreshold: 3
      
      volumes:
      - name: jenkins-casc-config
        configMap:
          name: jenkins-casc-config
  
  volumeClaimTemplates:
  - metadata:
      name: jenkins-home
    spec:
      accessModes: [ "ReadWriteMany" ]
      storageClassName: efs-sc
      resources:
        requests:
          storage: 100Gi
```

Create `jenkins-service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: jenkins
  namespace: jenkins-prod
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
spec:
  type: ClusterIP
  ports:
  - name: http
    port: 8080
    targetPort: 8080
  - name: jnlp
    port: 50000
    targetPort: 50000
  selector:
    app: jenkins-master
---
apiVersion: v1
kind: Service
metadata:
  name: jenkins-agent
  namespace: jenkins-prod
spec:
  type: ClusterIP
  clusterIP: None
  ports:
  - name: jnlp
    port: 50000
    targetPort: 50000
  selector:
    app: jenkins-master
```

Create `jenkins-ingress.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: jenkins
  namespace: jenkins-prod
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
    # Security headers
    alb.ingress.kubernetes.io/actions.ssl-redirect: '{"Type": "redirect", "RedirectConfig": { "Protocol": "HTTPS", "Port": "443", "StatusCode": "HTTP_301"}}'
spec:
  rules:
  - host: jenkins.yourcompany.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: jenkins
            port:
              number: 8080
  tls:
  - hosts:
    - jenkins.yourcompany.com
```

Deploy Jenkins:
```bash
# Apply configurations
kubectl apply -f jenkins-statefulset.yaml
kubectl apply -f jenkins-service.yaml
kubectl apply -f jenkins-ingress.yaml

# Wait for Jenkins to be ready
kubectl rollout status statefulset/jenkins-master -n jenkins-prod

# Get ALB DNS name
kubectl get ingress jenkins -n jenkins-prod -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

---

## Step 10: Configure DNS (Route53)

```bash
# Get ALB hostname
ALB_HOSTNAME=$(kubectl get ingress jenkins -n jenkins-prod \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

# Create Route53 record
aws route53 change-resource-record-sets \
  --hosted-zone-id YOUR_HOSTED_ZONE_ID \
  --change-batch '{
    "Changes": [{
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "jenkins.yourcompany.com",
        "Type": "CNAME",
        "TTL": 300,
        "ResourceRecords": [{"Value": "'$ALB_HOSTNAME'"}]
      }
    }]
  }'
```

---

## Step 11: Deploy Jenkins Passive Instance (Standby)

Create `jenkins-passive-statefulset.yaml`:

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: jenkins-master-passive
  namespace: jenkins-prod
  labels:
    app: jenkins-master-passive
spec:
  serviceName: jenkins-passive
  replicas: 0  # Start with 0, scale to 1 on failover
  selector:
    matchLabels:
      app: jenkins-master-passive
  template:
    metadata:
      labels:
        app: jenkins-master-passive
    spec:
      serviceAccountName: jenkins-sa
      securityContext:
        fsGroup: 1000
        runAsUser: 1000
      
      # Deploy in different AZ
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
            - matchExpressions:
              - key: topology.kubernetes.io/zone
                operator: NotIn
                values:
                - us-east-1a  # Assuming active is in us-east-1a
              - key: role
                operator: In
                values:
                - jenkins-master
      
      tolerations:
      - key: jenkins-master
        operator: Equal
        value: "true"
        effect: NoSchedule
      
      containers:
      - name: jenkins
        image: jenkins/jenkins:lts-jdk17
        # ... same configuration as active ...
        
        volumeMounts:
        - name: jenkins-home
          mountPath: /var/jenkins_home
        - name: jenkins-casc-config
          mountPath: /var/jenkins_home/casc_configs
      
      volumes:
      - name: jenkins-home
        persistentVolumeClaim:
          claimName: jenkins-home-jenkins-master-0  # Reference same PVC as active
      - name: jenkins-casc-config
        configMap:
          name: jenkins-casc-config
```

---

## Step 12: Create Failover Script

Create `jenkins-failover.sh`:

```bash
#!/bin/bash
set -e

NAMESPACE="jenkins-prod"
ACTIVE_STS="jenkins-master"
PASSIVE_STS="jenkins-master-passive"

echo "=== Jenkins HA Failover Script ==="

# Check active master health
ACTIVE_STATUS=$(kubectl get statefulset $ACTIVE_STS -n $NAMESPACE -o jsonpath='{.status.replicas}')
ACTIVE_READY=$(kubectl get statefulset $ACTIVE_STS -n $NAMESPACE -o jsonpath='{.status.readyReplicas}')

echo "Active Master Status: $ACTIVE_STATUS replicas, $ACTIVE_READY ready"

if [ "$ACTIVE_READY" != "1" ]; then
  echo "⚠️  Active master is unhealthy! Initiating failover..."
  
  # Scale down active master
  echo "Scaling down active master..."
  kubectl scale statefulset $ACTIVE_STS -n $NAMESPACE --replicas=0
  
  # Wait for active to terminate
  echo "Waiting for active master to terminate..."
  kubectl wait --for=delete pod/${ACTIVE_STS}-0 -n $NAMESPACE --timeout=300s || true
  
  # Scale up passive master
  echo "Scaling up passive master..."
  kubectl scale statefulset $PASSIVE_STS -n $NAMESPACE --replicas=1
  
  # Wait for passive to be ready
  echo "Waiting for passive master to be ready..."
  kubectl wait --for=condition=ready pod/${PASSIVE_STS}-0 -n $NAMESPACE --timeout=300s
  
  # Update service selector to point to passive
  kubectl patch service jenkins -n $NAMESPACE -p '{"spec":{"selector":{"app":"jenkins-master-passive"}}}'
  
  echo "✅ Failover completed! Passive master is now active."
  echo "New active pod: ${PASSIVE_STS}-0"
else
  echo "✅ Active master is healthy. No action needed."
fi
```

Make executable and test:
```bash
chmod +x jenkins-failover.sh
./jenkins-failover.sh
```

---

## Step 13: Setup Automated Backups

Create `jenkins-backup-cronjob.yaml`:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: jenkins-backup
  namespace: jenkins-prod
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM UTC
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 7
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: jenkins-backup-sa
          containers:
          - name: backup
            image: amazon/aws-cli:latest
            command:
            - /bin/sh
            - -c
            - |
              BACKUP_DATE=$(date +%Y%m%d-%H%M%S)
              BACKUP_NAME="jenkins-backup-${BACKUP_DATE}.tar.gz"
              
              echo "Starting Jenkins backup: $BACKUP_NAME"
              
              # Create backup
              cd /jenkins-home
              tar -czf /tmp/$BACKUP_NAME \
                --exclude='workspace/*' \
                --exclude='caches/*' \
                --exclude='logs/*' \
                .
              
              # Upload to S3
              aws s3 cp /tmp/$BACKUP_NAME \
                s3://jenkins-backups-prod/backups/$BACKUP_NAME \
                --storage-class STANDARD_IA
              
              # Cleanup old backups (keep last 30 days)
              aws s3 ls s3://jenkins-backups-prod/backups/ | \
                awk '{print $4}' | \
                sort -r | \
                tail -n +31 | \
                xargs -I {} aws s3 rm s3://jenkins-backups-prod/backups/{}
              
              echo "Backup completed: $BACKUP_NAME"
            
            volumeMounts:
            - name: jenkins-home
              mountPath: /jenkins-home
              readOnly: true
            
            env:
            - name: AWS_REGION
              value: us-east-1
          
          restartPolicy: OnFailure
          
          volumes:
          - name: jenkins-home
            persistentVolumeClaim:
              claimName: jenkins-home-jenkins-master-0
              readOnly: true
```

Create IAM role for backup:
```bash
# Create IAM policy for S3 backup
cat > jenkins-backup-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket",
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::jenkins-backups-prod",
        "arn:aws:s3:::jenkins-backups-prod/*"
      ]
    }
  ]
}
EOF

aws iam create-policy \
  --policy-name JenkinsBackupPolicy \
  --policy-document file://jenkins-backup-policy.json

# Create service account with IAM role
eksctl create iamserviceaccount \
  --name jenkins-backup-sa \
  --namespace jenkins-prod \
  --cluster jenkins-gitops-prod \
  --attach-policy-arn arn:aws:iam::ACCOUNT_ID:policy/JenkinsBackupPolicy \
  --approve

# Deploy backup CronJob
kubectl apply -f jenkins-backup-cronjob.yaml
```

---

## Disaster Recovery

### Recovery Time Objective (RTO): < 5 minutes
### Recovery Point Objective (RPO): < 24 hours

### Disaster Recovery Procedures

#### Scenario 1: Single Master Failure
```bash
# Automatic failover via health checks (< 2 minutes)
# Manual failover if needed:
./jenkins-failover.sh
```

#### Scenario 2: Complete Cluster Failure
```bash
# 1. Restore EKS cluster
eksctl create cluster -f eks-cluster.yaml

# 2. Restore EFS data from backup
aws s3 cp s3://jenkins-backups-prod/backups/latest.tar.gz /tmp/
# Mount EFS and extract

# 3. Redeploy Jenkins
kubectl apply -f jenkins-statefulset.yaml

# Total time: ~15-20 minutes
```

#### Scenario 3: Data Corruption
```bash
# Restore from S3 backup
BACKUP_DATE="20250119-020000"  # Replace with desired backup
aws s3 cp s3://jenkins-backups-prod/backups/jenkins-backup-${BACKUP_DATE}.tar.gz /tmp/

# Scale down Jenkins
kubectl scale statefulset jenkins-master -n jenkins-prod --replicas=0

# Restore data (exec into a debug pod with EFS mounted)
# Extract backup to EFS

# Scale up Jenkins
kubectl scale statefulset jenkins-master -n jenkins-prod --replicas=1
```

---

## Monitoring & Maintenance

### CloudWatch Integration

Create `cloudwatch-configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
  namespace: amazon-cloudwatch
data:
  fluent-bit.conf: |
    [SERVICE]
        Parsers_File /fluent-bit/parsers/parsers.conf
    
    [INPUT]
        Name tail
        Path /var/log/containers/jenkins-master*.log
        Parser docker
        Tag jenkins.*
    
    [OUTPUT]
        Name cloudwatch_logs
        Match jenkins.*
        region us-east-1
        log_group_name /aws/eks/jenkins-gitops-prod/jenkins
        log_stream_prefix jenkins-
        auto_create_group true
```

### Prometheus Monitoring

Create `jenkins-servicemonitor.yaml`:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: jenkins
  namespace: jenkins-prod
spec:
  selector:
    matchLabels:
      app: jenkins-master
  endpoints:
  - port: http
    path: /prometheus
    interval: 30s
```

### Grafana Dashboard

Import Jenkins dashboard ID: 9964

---

## Security Best Practices

### 1. Network Policies

Create `jenkins-network-policy.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: jenkins-master
  namespace: jenkins-prod
spec:
  podSelector:
    matchLabels:
      app: jenkins-master
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: jenkins-prod
    ports:
    - protocol: TCP
      port: 8080
    - protocol: TCP
      port: 50000
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 443
    - protocol: TCP
      port: 80
```

### 2. Pod Security Policy

Create `jenkins-psp.yaml`:

```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: jenkins-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
  - ALL
  volumes:
  - 'configMap'
  - 'emptyDir'
  - 'persistentVolumeClaim'
  - 'secret'
  hostNetwork: false
  hostIPC: false
  hostPID: false
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'RunAsAny'
  fsGroup:
    rule: 'RunAsAny'
```

### 3. Secrets Encryption

Enable encryption at rest for Kubernetes secrets:

```bash
# Create KMS key
aws kms create-key --description "EKS Jenkins Secrets Encryption"

# Enable encryption on cluster
aws eks associate-encryption-config \
  --cluster-name jenkins-gitops-prod \
  --encryption-config '[{"resources":["secrets"],"provider":{"keyArn":"arn:aws:kms:us-east-1:ACCOUNT_ID:key/KEY_ID"}}]'
```

---

## Cost Optimization

### Estimated Monthly Cost (AWS us-east-1)

| Resource | Configuration | Monthly Cost |
|----------|--------------|--------------|
| EKS Control Plane | 1 cluster | $73 |
| EC2 Nodes (On-Demand) | 2x t3.xlarge | ~$240 |
| EC2 Nodes (Spot) | 3x t3.large (avg) | ~$90 |
| EFS | 100GB + throughput | ~$35 |
| ALB | 1 ALB | ~$25 |
| NAT Gateway | 2 AZs | ~$65 |
| Data Transfer | 1TB/month | ~$90 |
| S3 Backups | 500GB IA | ~$10 |
| CloudWatch | Logs + Metrics | ~$20 |
| **Total** | | **~$648/month** |

### Cost Savings Tips

1. **Use Spot Instances** for Jenkins agents (60-70% savings)
2. **Cluster Autoscaling** - scale down during off-hours
3. **EFS Lifecycle** - move old data to Infrequent Access
4. **Reserved Instances** - 1-year commit for master nodes (40% savings)

---

## Production Checklist

- [ ] EKS cluster created with multi-AZ
- [ ] EFS provisioned and mounted
- [ ] Jenkins StatefulSet deployed (active)
- [ ] Jenkins StatefulSet configured (passive)
- [ ] Health checks configured
- [ ] Failover script tested
- [ ] ALB/Ingress configured with SSL
- [ ] DNS (Route53) configured
- [ ] ArgoCD deployed in HA mode
- [ ] Backup CronJob created and tested
- [ ] CloudWatch logging enabled
- [ ] Prometheus monitoring configured
- [ ] Network policies applied
- [ ] Secrets encrypted with KMS
- [ ] IAM roles configured (IRSA)
- [ ] Security groups reviewed
- [ ] Disaster recovery plan documented
- [ ] Team trained on failover procedures

---

## Support & Troubleshooting

### Common Issues

**Issue 1: EFS mount fails**
```bash
# Check EFS mount targets
aws efs describe-mount-targets --file-system-id $EFS_ID

# Check security group allows NFS (port 2049)
aws ec2 describe-security-groups --group-ids $SG_ID
```

**Issue 2: Pod stuck in Pending**
```bash
# Check events
kubectl describe pod jenkins-master-0 -n jenkins-prod

# Check PVC status
kubectl get pvc -n jenkins-prod
```

**Issue 3: Slow failover**
```bash
# Tune https://bitbucket.org/hotelkey/hk-k8s/pull-requests/477/diff intervals
# Edit StatefulSet:
# livenessProbe.periodSeconds: 10
# livenessProbe.failureThreshold: 3
# = 30 seconds detection time
```

---

## Contacts & References

- **AWS EKS Documentation**: https://docs.aws.amazon.com/eks/
- **Jenkins Documentation**: https://www.jenkins.io/doc/
- **ArgoCD Documentation**: https://argo-cd.readthedocs.io/
- **Kubernetes Best Practices**: https://kubernetes.io/docs/concepts/configuration/overview/

---

**Document Version**: 1.0  
**Last Updated**: November 19, 2025  
**Author**: DevOps Team  
**Review Cycle**: Quarterly
