# EKS ALB 헬름 차트 배포 가이드

## 개요
이 헬름 차트는 EKS 클러스터에서 AWS Application Load Balancer (ALB)를 사용하여 Spring Boot 애플리케이션을 배포하기 위한 설정입니다.

## 구조
```
helm/
├── backend/          # 메인 애플리케이션 차트
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── spring-deployment.yaml
│       ├── spring-service.yaml
│       ├── redis-deployment.yaml
│       ├── redis-service.yaml
│       ├── secret.yaml          # 환경변수 Secret
│       └── ingress.yaml
├── gateway/          # 선택적 게이트웨이 차트 (기본 비활성화)
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       └── ingress.yaml
├── scripts/
│   └── deploy-with-secrets.sh   # 배포 스크립트
└── env.example                   # 환경변수 예시 파일
```

## 사전 요구사항

### 1. EKS 클러스터 설정
```bash
# AWS Load Balancer Controller 설치
kubectl apply -k "github.com/aws/eks-charts/stable/aws-load-balancer-controller//crds?ref=master"

# IAM OIDC Provider 설정
eksctl utils associate-iam-oidc-provider --region=ap-northeast-2 --cluster=your-cluster-name --approve

# AWS Load Balancer Controller 배포
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=your-cluster-name \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### 2. SSL 인증서 설정
AWS Certificate Manager에서 도메인 인증서를 생성하고 `values.yaml`의 `certificate-arn`을 업데이트하세요.

### 3. ECR 시크릿 설정
```bash
kubectl create secret docker-registry ecr-secret \
  --docker-server=266735804784.dkr.ecr.ap-northeast-2.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region ap-northeast-2)
```

## 환경변수 설정

### 방법 1: .env 파일 사용 (권장)
```bash
# 1. 환경변수 파일 생성
cp helm/env.example .env

# 2. .env 파일 편집하여 실제 값 입력
vim .env

# 3. 배포 스크립트 실행
chmod +x helm/scripts/deploy-with-secrets.sh
./helm/scripts/deploy-with-secrets.sh default backend
```

### 방법 2: values.yaml 직접 편집
```bash
# values.yaml의 secrets 섹션에 직접 값 입력
vim helm/backend/values.yaml

# 헬름으로 배포
helm install backend ./helm/backend --namespace default
```

### 방법 3: 배포 후 수동 설정
```bash
# 1. 기본 배포
helm install backend ./helm/backend --namespace default

# 2. Secret 수동 편집
kubectl edit secret backend-backend-secret -n default
```

## 배포 방법

### 1. 자동 배포 스크립트 사용 (권장)
```bash
# 스크립트 실행 권한 부여
chmod +x helm/scripts/deploy-with-secrets.sh

# 배포 실행
./helm/scripts/deploy-with-secrets.sh [namespace] [release-name]

# 예시
./helm/scripts/deploy-with-secrets.sh default backend
```

### 2. 수동 헬름 배포
```bash
# Backend 차트 배포
helm install backend ./helm/backend \
  --namespace default \
  --set image.tag=latest

# 배포 상태 확인
kubectl get pods,svc,ingress
```

### 3. Gateway 차트 배포 (선택사항)
```bash
# Gateway 차트 활성화하여 배포
helm install gateway ./helm/gateway \
  --namespace default \
  --set ingress.enabled=true
```

## 주요 설정

### ALB Ingress 설정
- **Scheme**: `internet-facing` - 외부에서 접근 가능
- **Target Type**: `ip` - Pod IP 직접 타겟팅
- **Protocol**: HTTP/HTTPS 지원
- **SSL Redirect**: HTTP → HTTPS 자동 리다이렉트
- **Health Check**: `/actuator/health` 엔드포인트 사용

### 경로 설정
- `/api` - API 엔드포인트
- `/actuator/health` - 헬스체크
- `/healthz` - 쿠버네티스 헬스체크
- `/oauth/kakao/callback` - 카카오 OAuth 콜백
- `/` - 루트 경로

### 리소스 설정
- **Memory**: 256Mi (요청) / 512Mi (제한)
- **CPU**: 250m (요청) / 500m (제한)

## 환경변수 관리

### Secret 구조
모든 환경변수는 Kubernetes Secret으로 관리됩니다:
- **데이터베이스 설정**: URL, 사용자명, 비밀번호
- **Redis 설정**: 호스트, 포트
- **카카오 OAuth**: Admin Key, Client ID, Secret
- **AWS S3**: Access Key, Secret Key, 버킷명
- **JWT**: Secret Key
- **기타**: 프로필, 포트 등

### 환경변수 업데이트
```bash
# Secret 직접 편집
kubectl edit secret backend-backend-secret -n default

# 또는 헬름 업그레이드
helm upgrade backend ./helm/backend \
  --set secrets.SPRING_DATASOURCE_URL="new-url"
```

## 모니터링 및 로그

### 로그 확인
```bash
# Pod 로그 확인
kubectl logs -f deployment/backend-backend

# ALB 로그 확인 (CloudWatch)
aws logs describe-log-groups --log-group-name-prefix "/aws/applicationloadbalancer"
```

### 헬스체크 확인
```bash
# Pod 상태 확인
kubectl get pods -l app=backend-backend

# 서비스 엔드포인트 확인
kubectl get endpoints backend-backend

# Ingress 상태 확인
kubectl describe ingress backend-backend
```

## 트러블슈팅

### 일반적인 문제들

1. **ALB가 생성되지 않는 경우**
   - AWS Load Balancer Controller가 설치되어 있는지 확인
   - IAM 권한이 올바르게 설정되어 있는지 확인

2. **Pod가 시작되지 않는 경우**
   - ECR 시크릿이 올바르게 설정되어 있는지 확인
   - 이미지 태그가 올바른지 확인
   - 환경변수가 올바르게 설정되어 있는지 확인

3. **SSL 인증서 오류**
   - ACM 인증서 ARN이 올바른지 확인
   - 도메인 이름이 인증서와 일치하는지 확인

4. **환경변수 오류**
   - Secret이 올바르게 생성되었는지 확인
   - 환경변수 이름이 애플리케이션과 일치하는지 확인

### 디버깅 명령어
```bash
# Pod 이벤트 확인
kubectl describe pod <pod-name>

# Secret 내용 확인
kubectl get secret backend-backend-secret -o yaml

# 환경변수 확인
kubectl exec <pod-name> -- env | grep SPRING

# 서비스 엔드포인트 확인
kubectl get endpoints

# Ingress 이벤트 확인
kubectl describe ingress

# ALB 상태 확인
aws elbv2 describe-load-balancers
```

## 업데이트 및 롤백

### 업데이트
```bash
# 새 이미지로 업데이트
helm upgrade backend ./helm/backend \
  --set image.tag=new-tag

# 환경변수 변경으로 업데이트
helm upgrade backend ./helm/backend \
  --set secrets.SPRING_DATASOURCE_URL="new-url"
```

### 롤백
```bash
# 이전 버전으로 롤백
helm rollback backend 1

# 특정 리비전으로 롤백
helm rollback backend 2
```

## 보안 고려사항

1. **네트워크 정책**: 필요한 포트만 열어두기
2. **RBAC**: 최소 권한 원칙 적용
3. **시크릿 관리**: Kubernetes Secrets 사용
4. **SSL/TLS**: HTTPS 강제 적용
5. **헬스체크**: 적절한 헬스체크 엔드포인트 설정
6. **환경변수**: 민감한 정보는 Secret으로 관리

## 비용 최적화

1. **ALB 그룹**: 동일한 ALB 그룹 사용으로 비용 절약
2. **리소스 제한**: 적절한 CPU/메모리 제한 설정
3. **오토스케일링**: HPA 설정으로 리소스 효율성 증대
4. **스팟 인스턴스**: 비용 절약을 위한 스팟 인스턴스 활용 