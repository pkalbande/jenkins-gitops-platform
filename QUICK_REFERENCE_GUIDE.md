# Production Setup Quick Reference Guide

## 🚀 Quick Start Commands

### Initial Setup (One-time)
```bash
# 1. Create EKS Cluster (~15 minutes)
eksctl create cluster -f eks-cluster.yaml

# 2. Create EFS
EFS_ID=$(aws efs create-file-system --performance-mode generalPurpose --encrypted --query 'FileSystemId' --output text)

# 3. Install Controllers
kubectl apply -f https://github.com/kubernetes-sigs/aws-efs-csi-driver/releases/latest/download/kubernetes.yaml
helm install aws-load-balancer-controller eks/aws-load-balancer-controller -n kube-system

# 4. Deploy ArgoCD
kubectl create namespace argocd
helm install argocd argo/argo-cd -n argocd -f argocd-ha-values.yaml

# 5. Deploy Jenkins
kubectl create namespace jenkins-prod
kubectl apply -f jenkins-statefulset.yaml
kubectl apply -f jenkins-service.yaml
kubectl apply -f jenkins-ingress.yaml

# 6. Configure Failover
kubectl apply -f jenkins-passive-statefulset.yaml
kubectl apply -f jenkins-backup-cronjob.yaml
```

---

## 📊 Architecture Flow Diagrams

### CI/CD Pipeline Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CI/CD Pipeline Flow                           │
└──────────────────────────────────────────────────────────────────────┘

Developer                  GitHub                   Jenkins
   │                         │                         │
   │  1. git push            │                         │
   ├────────────────────────►│                         │
   │                         │                         │
   │                         │  2. Webhook             │
   │                         ├────────────────────────►│
   │                         │                         │
   │                         │                         │  3. Checkout
   │                         │◄────────────────────────┤     Code
   │                         │                         │
   │                         │                         │  4. Build
   │                         │                         │     Docker Image
   │                         │                         │
   │                         │                         │  5. Run Tests
   │                         │                         │
   │                         │                         │  6. Push to ECR
   │                         │                         ├──────────────┐
   │                         │                         │              │
   │                         │                         │              ▼
   │                         │                         │         ┌────────┐
   │                         │                         │         │  ECR   │
   │                         │                         │         └────────┘
   │                         │  7. Update              │
   │                         │     GitOps Repo         │
   │                         │◄────────────────────────┤
   │                         │                         │
   │                         │                         │
   │                         │                    ArgoCD
   │                         │                         │
   │                         │  8. Detect Change       │
   │                         ├────────────────────────►│
   │                         │                         │
   │                         │                         │  9. Sync to
   │                         │                         │     Kubernetes
   │                         │                         │
   │                         │                         ▼
   │                         │                    ┌────────────┐
   │                         │                    │  EKS       │
   │                         │                    │  Cluster   │
   │                         │                    └────────────┘
   │                         │                         │
   │  10. Notification       │                         │
   │◄────────────────────────┴─────────────────────────┘
   │      (Slack/Email)
   │
```

### High Availability Failover Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│                    HA Failover Decision Tree                          │
└──────────────────────────────────────────────────────────────────────┘

                        ┌─────────────────┐
                        │  Health Check   │
                        │  Every 30s      │
                        └────────┬────────┘
                                 │
                    ┌────────────▼────────────┐
                    │  Is Active Master       │
                    │  Responding?            │
                    └────────┬────────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
              YES                        NO
                │                         │
                ▼                         ▼
        ┌───────────────┐        ┌──────────────────┐
        │  Continue     │        │  Failure Count   │
        │  Monitoring   │        │  Increment       │
        └───────────────┘        └────────┬─────────┘
                                           │
                                  ┌────────▼─────────┐
                                  │  Failures > 5?   │
                                  └────────┬─────────┘
                                           │
                              ┌────────────┴────────────┐
                              │                         │
                            YES                        NO
                              │                         │
                              ▼                         ▼
                    ┌──────────────────┐       ┌────────────┐
                    │  TRIGGER         │       │  Wait      │
                    │  FAILOVER        │       │  30s       │
                    └────────┬─────────┘       └────────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │  1. Scale Active │
                    │     to 0         │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │  2. Wait for     │
                    │     Termination  │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │  3. Scale Passive│
                    │     to 1         │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │  4. Update       │
                    │     Service      │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │  5. Send Alert   │
                    │     to Team      │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │  FAILOVER        │
                    │  COMPLETE        │
                    │  (~2 minutes)    │
                    └──────────────────┘
```

### Data Flow & Storage Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Data Flow & Storage                                │
└──────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────┐
                    │     Developer           │
                    │     Commits Code        │
                    └───────────┬─────────────┘
                                │
                                ▼
                    ┌─────────────────────────┐
                    │     GitHub              │
                    │     (Source Code)       │
                    └───────────┬─────────────┘
                                │
                                ▼
        ┌──────────────────────────────────────────────┐
        │          Jenkins Build Process               │
        │                                              │
        │  ┌────────┐    ┌────────┐    ┌────────┐   │
        │  │ Clone  │───►│ Build  │───►│  Test  │   │
        │  └────────┘    └────────┘    └────────┘   │
        │                                │            │
        │                                ▼            │
        │                        ┌───────────────┐   │
        │                        │  Docker Build │   │
        │                        └───────┬───────┘   │
        │                                │            │
        └────────────────────────────────┼────────────┘
                                         │
                        ┌────────────────┴───────────────┐
                        │                                │
                        ▼                                ▼
            ┌─────────────────────┐        ┌──────────────────────┐
            │     Amazon ECR      │        │    Artifact Store    │
            │  (Container Images) │        │    - Jenkins Home    │
            │                     │        │    - Build Logs      │
            │  - app1-node:1.0.0  │        │    - Test Results    │
            │  - app2-python:2.0  │        │    - Metadata        │
            └─────────────────────┘        └──────────┬───────────┘
                        │                             │
                        │                             │
                        │                    ┌────────▼──────────┐
                        │                    │      EFS          │
                        │                    │  (Shared Storage) │
                        │                    │                   │
                        │                    │  /jenkins-home    │
                        │                    │   ├── jobs/       │
                        │                    │   ├── builds/     │
                        │                    │   ├── workspace/  │
                        │                    │   └── logs/       │
                        │                    └────────┬──────────┘
                        │                             │
                        │             ┌───────────────┴──────────────┐
                        │             │                              │
                        │             ▼                              ▼
                        │   ┌─────────────────┐          ┌─────────────────┐
                        │   │  Jenkins Master │          │  Jenkins Master │
                        │   │    (Active)     │          │   (Passive)     │
                        │   │   AZ-1          │          │   AZ-2          │
                        │   └─────────────────┘          └─────────────────┘
                        │
                        ▼
            ┌─────────────────────┐
            │   GitOps Repo       │
            │   (Updated)         │
            │                     │
            │  environments/      │
            │   ├── dev/          │
            │   ├── test/         │
            │   ├── stage/        │
            │   └── prod/         │
            └─────────┬───────────┘
                      │
                      ▼
            ┌─────────────────────┐
            │     ArgoCD          │
            │   (Sync)            │
            └─────────┬───────────┘
                      │
                      ▼
            ┌─────────────────────┐
            │   Kubernetes        │
            │   Deployment        │
            │   (Running Apps)    │
            └─────────────────────┘
```

---

## 🔧 Daily Operations

### Check Jenkins Status
```bash
# Check active master
kubectl get pods -n jenkins-prod -l app=jenkins-master

# Check service status
kubectl get svc jenkins -n jenkins-prod

# Get Jenkins URL
kubectl get ingress jenkins -n jenkins-prod -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

# View logs
kubectl logs -f jenkins-master-0 -n jenkins-prod
```

### Manual Failover Test
```bash
# Simulate failure
kubectl delete pod jenkins-master-0 -n jenkins-prod

# Watch recovery
kubectl get pods -n jenkins-prod -w

# Verify new pod is running
kubectl get pods -n jenkins-prod
```

### Backup Operations
```bash
# Manual backup
kubectl create job --from=cronjob/jenkins-backup jenkins-backup-manual -n jenkins-prod

# List backups
aws s3 ls s3://jenkins-backups-prod/backups/

# Download specific backup
aws s3 cp s3://jenkins-backups-prod/backups/jenkins-backup-20250119.tar.gz .
```

### Restore from Backup
```bash
# 1. Scale down Jenkins
kubectl scale statefulset jenkins-master -n jenkins-prod --replicas=0

# 2. Download backup
aws s3 cp s3://jenkins-backups-prod/backups/latest.tar.gz /tmp/

# 3. Create restore pod
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: jenkins-restore
  namespace: jenkins-prod
spec:
  containers:
  - name: restore
    image: busybox
    command: ['sh', '-c', 'sleep 3600']
    volumeMounts:
    - name: jenkins-home
      mountPath: /jenkins-home
  volumes:
  - name: jenkins-home
    persistentVolumeClaim:
      claimName: jenkins-home-jenkins-master-0
EOF

# 4. Copy backup to pod
kubectl cp /tmp/latest.tar.gz jenkins-prod/jenkins-restore:/tmp/

# 5. Extract backup
kubectl exec -it jenkins-restore -n jenkins-prod -- sh -c "cd /jenkins-home && tar -xzf /tmp/latest.tar.gz"

# 6. Cleanup and restart
kubectl delete pod jenkins-restore -n jenkins-prod
kubectl scale statefulset jenkins-master -n jenkins-prod --replicas=1
```

---

## 📈 Monitoring Dashboards

### Key Metrics to Monitor

| Metric | Threshold | Alert |
|--------|-----------|-------|
| Pod CPU Usage | > 80% | Warning |
| Pod Memory Usage | > 85% | Warning |
| Pod Restart Count | > 3/hour | Critical |
| Disk Usage (EFS) | > 80% | Warning |
| Build Queue Length | > 10 | Warning |
| Build Success Rate | < 90% | Warning |
| Response Time (p95) | > 2s | Warning |

### CloudWatch Queries

```bash
# Get pod CPU usage
aws cloudwatch get-metric-statistics \
  --namespace AWS/EKS \
  --metric-name pod_cpu_utilization \
  --dimensions Name=ClusterName,Value=jenkins-gitops-prod \
  --start-time 2025-01-19T00:00:00Z \
  --end-time 2025-01-19T23:59:59Z \
  --period 300 \
  --statistics Average

# Get error rate
aws logs tail /aws/eks/jenkins-gitops-prod/jenkins \
  --follow \
  --filter-pattern "ERROR"
```

---

## 🚨 Incident Response

### Severity Levels

**P0 - Critical (< 15 min response)**
- Jenkins completely down
- Data loss detected
- Security breach

**P1 - High (< 1 hour response)**
- Active master unhealthy but passive available
- Builds failing consistently
- Performance degradation > 50%

**P2 - Medium (< 4 hours response)**
- Single build failure
- Backup job failed
- Non-critical plugin issue

### Emergency Contacts

```
Primary On-Call: +1-XXX-XXX-XXXX
Secondary On-Call: +1-XXX-XXX-XXXX
Manager: +1-XXX-XXX-XXXX
AWS Support: Case Portal
```

### Runbook Links

1. [Jenkins Master Down](./runbooks/jenkins-master-down.md)
2. [EFS Issues](./runbooks/efs-issues.md)
3. [Build Failures](./runbooks/build-failures.md)
4. [Security Incidents](./runbooks/security-incidents.md)

---

## 💰 Cost Tracking

### Monthly Cost Breakdown Script

```bash
#!/bin/bash
# get-jenkins-costs.sh

START_DATE="2025-01-01"
END_DATE="2025-01-31"

echo "=== Jenkins Infrastructure Costs ==="
echo "Period: $START_DATE to $END_DATE"
echo ""

# EKS Cluster
echo "EKS Control Plane: $73.00"

# EC2 Instances
echo -n "EC2 Instances: "
aws ce get-cost-and-usage \
  --time-period Start=$START_DATE,End=$END_DATE \
  --granularity MONTHLY \
  --metrics UnblendedCost \
  --filter file://<(cat <<EOF
{
  "Dimensions": {
    "Key": "SERVICE",
    "Values": ["Amazon Elastic Compute Cloud - Compute"]
  }
}
EOF
) \
  --query 'ResultsByTime[0].Total.UnblendedCost.Amount' \
  --output text

# Add other services...
```

---

## 📚 Additional Resources

### Training Materials
- [Jenkins Administration Course](https://www.jenkins.io/doc/book/)
- [Kubernetes for Jenkins](https://kubernetes.io/docs/tutorials/)
- [AWS EKS Workshop](https://www.eksworkshop.com/)

### Useful Commands Cheat Sheet

```bash
# Quick status check
alias jenkins-status='kubectl get pods,svc,ingress -n jenkins-prod'

# Quick logs
alias jenkins-logs='kubectl logs -f jenkins-master-0 -n jenkins-prod'

# Quick failover
alias jenkins-failover='./jenkins-failover.sh'

# Quick backup
alias jenkins-backup='kubectl create job --from=cronjob/jenkins-backup jenkins-backup-manual -n jenkins-prod'

# Port forward for debugging
alias jenkins-forward='kubectl port-forward svc/jenkins -n jenkins-prod 8080:8080'
```

### Troubleshooting Tips

1. **Pod won't start**: Check PVC binding, EFS mounts, secrets
2. **Slow performance**: Check resource limits, add more agents
3. **Backup fails**: Check IAM permissions, S3 bucket policy
4. **Can't access UI**: Check ALB, security groups, DNS

---

**Quick Reference Version**: 1.0  
**Last Updated**: November 19, 2025
