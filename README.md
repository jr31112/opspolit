# OpsPilot

Spring API를 하나의 서비스로 삼아 애플리케이션 개발부터 인프라, 배포, 모니터링, 장애 대응까지 운영 전 과정을 구축하는 DevOps 프로젝트입니다.

## Overview

OpsPilot은 애플리케이션과 플랫폼을 분리된 작업으로 보지 않습니다. 하나의 저장소에서 API를 개발하고, 컨테이너화하고, Kubernetes 환경에 배포한 뒤, 관측과 장애 대응까지 검증하는 것을 목표로 합니다.

```text
Application
    Spring Boot + JPA + MySQL
                    |
                    v
Container
    Docker + Docker Hub
                    |
                    v
Platform
    Terraform + AWS + K3s
                    |
                    v
Operations
    CI/CD + Monitoring + HPA + DR
```

## Current Status

| 단계 | 주제 | 상태 |
| --- | --- | --- |
| 0단계 | Spring API 기본 구현 및 로컬 실행 환경 | 완료 |
| 1단계 | Terraform 기반 인프라와 K3s 연결 | 예정 |
| 2단계 | CI/CD 및 모니터링 구축 | 예정 |
| 3단계 | 고도화: HPA 검증 및 DR 테스트 | 예정 |

## Stage 0. Application Baseline

현재 완료된 애플리케이션 기반 단계입니다. 로컬 테스트까지 완료했으며, Docker 컨테이너는 실행이 필요한 시점에 `docker compose up --build`로 구동합니다.

### Application

- Spring Boot API 구현
- Spring Web MVC 기반 HTTP 요청 처리
- Spring Data JPA 기반 데이터 접근
- MySQL 연동
- 기본 테스트 구성
- Docker Compose를 이용한 로컬 실행 환경

### Tech Stack

| 영역 | 기술 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.1.1, Spring Web MVC |
| Persistence | Spring Data JPA, Hibernate |
| Database | MySQL 8.0 |
| Build | Gradle Wrapper |
| Container | Docker, Docker Compose |

### Local Run

#### Requirements

- Java 17+
- Docker and Docker Compose

#### Environment Variables

프로젝트 루트에 `.env` 파일을 생성합니다. `.env`는 Git에 커밋하지 않습니다.

```dotenv
DB_ROOT_PASSWORD=opspilot
DB_NAME=opspilot
DB_USERNAME=opspilot
DB_PASSWORD=opspilot
SPRING_PROFILES_ACTIVE=local
```

#### Docker Compose

```bash
docker compose up --build
```

API는 `http://localhost:8080`에서 실행됩니다.

```bash
docker compose down
```

#### Test

```bash
./gradlew test
```

## Stage 1. Infrastructure and K3s

애플리케이션을 실제 운영 환경에 배포하기 위한 기반을 구성합니다.

### Terraform

- AWS 리소스를 코드로 관리
- 네트워크, 컴퓨팅 리소스, 보안 그룹 구성
- 환경별 인프라 변경 이력 관리
- `terraform plan`과 `terraform apply`를 통한 재현 가능한 변경

### K3s

- 경량 Kubernetes 클러스터 구성
- 애플리케이션 Deployment와 Service 배포
- Docker 이미지 기반 애플리케이션 실행
- 로컬 또는 AWS 환경에서 클러스터 연결 검증

### Tech Stack

| 영역 | 기술 |
| --- | --- |
| Infrastructure as Code | Terraform |
| Cloud | AWS |
| Orchestration | K3s, Kubernetes |
| Container | Docker, Docker Hub |
| Configuration | Kubernetes YAML |

### Completion Criteria

- [ ] AWS 인프라를 Terraform으로 생성하고 변경 사항을 재현할 수 있음
- [ ] K3s 클러스터가 정상적으로 구성되고 노드 상태를 확인할 수 있음
- [ ] K3s에서 Spring API와 MySQL이 정상적으로 배포됨
- [ ] 외부 요청이 애플리케이션 Service까지 도달함
- [ ] 장애 발생 시 Kubernetes 리소스 상태와 로그로 원인을 확인할 수 있음

## Stage 2. CI/CD and Observability

코드 변경이 이미지 빌드, 테스트, 배포와 모니터링으로 이어지는 흐름을 구축합니다.

### CI/CD

- GitHub Actions로 테스트 자동화
- Docker 이미지 빌드 및 Docker Hub push
- 커밋 또는 태그 기반 이미지 버전 관리
- K3s 배포 단계 자동화
- 배포 실패 시 원인 확인과 롤백 절차 검증

### Monitoring and Logging

- Prometheus로 애플리케이션과 Kubernetes 메트릭 수집
- Grafana 대시보드 구성
- 애플리케이션 로그와 컨테이너 로그 수집
- CPU, 메모리, 요청 수, 응답 시간, 에러율 관측
- 장애 상황을 확인할 수 있는 알림 기준 정의

### Delivery Flow

```text
Git Push
     |
     v
GitHub Actions
     |-- Test
     |-- Build Docker Image
     |-- Push Image to Docker Hub
     v
K3s Deployment
     |-- Rollout Status Check
     |-- Rollback on Failure
     v
Prometheus / Grafana
```

### Tech Stack

| 영역 | 기술 |
| --- | --- |
| Image Registry | Docker Hub |
| Metrics | Prometheus |
| Dashboard | Grafana |
| Logging | Kubernetes / Container Logging |
| Deployment | K3s, Kubernetes |

### Completion Criteria

- [ ] GitHub Actions에서 테스트가 자동으로 실행됨
- [ ] Docker 이미지가 커밋 또는 태그 기준으로 Docker Hub에 등록됨
- [ ] 새 이미지가 K3s 환경에 자동 배포됨
- [ ] 배포 성공 여부와 롤백 절차를 확인할 수 있음
- [ ] Prometheus에서 애플리케이션과 Kubernetes 메트릭을 수집함
- [ ] Grafana에서 요청 수, 응답 시간, 에러율, CPU, 메모리를 확인함
- [ ] 주요 장애 상황에 대한 로그와 알림 기준이 정의됨

## Stage 3. Advanced Operations

트래픽 변화와 장애 상황을 가정해 시스템의 자동 확장과 복구 능력을 검증합니다.

### HPA Validation

- CPU 및 메모리 기반 Horizontal Pod Autoscaler 구성
- 부하를 주어 Pod 증가와 축소 동작 검증
- 확장까지 걸리는 시간과 최대 Pod 수 측정
- 리소스 requests / limits와 HPA 동작의 관계 확인

### Disaster Recovery Test

- 애플리케이션 장애 및 Pod 삭제 시나리오
- 데이터베이스 장애와 복구 시나리오
- 노드 장애 및 서비스 재배포 검증
- MySQL 백업과 복구 시나리오 검증
- 복구 후 데이터 정합성 확인
- 백업과 복구 절차 문서화
- RTO / RPO 기준 수립 및 측정

### Tech Stack

| 영역 | 기술 |
| --- | --- |
| Scaling | Kubernetes HPA |
| Load Test | 부하 테스트 도구 |
| Recovery | Kubernetes Rollout, Backup / Restore |
| Observability | Prometheus, Grafana, Logging |
| Infrastructure | Terraform, AWS, K3s |

### Completion Criteria

- [ ] 부하 증가에 따라 HPA가 Pod를 확장함
- [ ] 부하 감소 후 Pod가 설정된 최소 개수까지 축소됨
- [ ] CPU / 메모리 requests와 limits가 HPA 기준에 맞게 설정됨
- [ ] 애플리케이션 Pod 삭제 후 서비스가 자동으로 복구됨
- [ ] 노드 장애 또는 재배포 후 서비스가 정상화됨
- [ ] MySQL 백업본으로 데이터를 복구할 수 있음
- [ ] 복구 시간과 데이터 유실 범위를 측정해 RTO / RPO를 기록함

## Repository Structure

```text
.
├─ src/                     # Spring Boot application
├─ docker-compose.yml       # Local application + MySQL
├─ dockerfile               # Multi-stage application image
├─ build.gradle             # Gradle dependencies and tasks
├─ terraform/               # Stage 1: AWS infrastructure (planned)
├─ k8s/                     # Stage 1: K3s manifests (planned)
├─ .github/workflows/       # Stage 2: GitHub Actions (planned)
└─ monitoring/              # Stage 2: Prometheus / Grafana (planned)
```

## Target Flow

```text
Code Push
     |
     v
GitHub Actions
     |-- Test
     |-- Build Docker Image
     |-- Push to Docker Hub
     v
K3s Deployment
     |-- Prometheus Metrics
     |-- Grafana Dashboard
     |-- HPA Scaling
     v
Failure / Recovery Test
```

## Ownership

```text
Codex
 └─ Application
         ├─ Spring API 구현
         ├─ JPA / MySQL
         ├─ 테스트
         └─ 버그 수정

나
 └─ Platform / Ops
         ├─ AWS / Terraform
         ├─ Docker / K3s
         ├─ GitHub Actions / Docker Hub
         ├─ Prometheus / Grafana
         ├─ Logging
         ├─ Resource / HPA
         ├─ 장애 주입
         ├─ DR
         └─ 비용 최적화
```
