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

## Architecture Plan

현재는 로컬 환경에서 Spring API와 MySQL을 검증하고 있습니다. 이후 최소 비용의 단일 인스턴스 환경에서 시작해, 리소스 여유가 생기면 관측 시스템을 추가하고, 마지막에 데이터베이스와 장애 도메인을 분리합니다.

### Stage 0. Local Architecture

```text
Developer
    |
    | ./gradlew test / docker compose up
    v
Spring Boot App :8080  -------->  MySQL :3306
       app container                  mysql container
```

### Stage 1. Infrastructure Architecture

Terraform으로 AWS의 `t3.small` 인스턴스 1개를 생성하고, 해당 인스턴스에 단일 노드 K3s 클러스터를 구성합니다. 애플리케이션과 MySQL은 Docker 이미지를 사용해 같은 K3s 클러스터 안에서 실행합니다.

```text
                       Terraform
                           |
                           v
                   AWS EC2: t3.small
                           |
                           v
                  Single-node K3s Cluster
                    /                    \
                   /                      \
        Spring API Deployment       MySQL Deployment
                   |                      |
                   v                      v
             Spring API Service     MySQL Service
```

### Stage 2. Delivery and Observability Architecture

코드 변경을 테스트, 이미지 빌드, Docker Hub push, K3s 배포까지 연결합니다. HPA와 DR 검증에 필요한 관측 기반을 확보하기 위해 모니터링은 제외하지 않고, 단일 인스턴스에서도 동작할 수 있도록 경량 구성으로 운영합니다.

```text
GitHub Repository
       |
       v
GitHub Actions
  |-- Test
  |-- Docker Build
  `-- Push to Docker Hub
       |
       v
K3s Cluster
  |-- Spring API
  |-- MySQL
     |-- Metrics Server <----- HPA resource metrics
     |-- Prometheus <----- Application / Node Metrics
     `-- Grafana   <----- Prometheus
       |
       v
     Dashboard / Alert
```

### Stage 3. Resilience Architecture

EC2 인스턴스를 AZ별로 1대씩 구성하고, 데이터베이스는 AWS RDS MySQL로 분리합니다. AZ-A는 K3s 관리 노드, 모니터링, 애플리케이션을 담당하며 RDS MySQL의 기본 배치 영역도 AZ-A로 구성합니다. AZ-B는 애플리케이션만 실행합니다. 외부 트래픽은 `ALB -> EC2 -> K3s NodePort -> Application Pod` 경로로 전달하며, ALB health check가 AZ-B 장애를 감지하면 AZ-A NodePort로 요청을 전환합니다.

```text
Client
     |
     v
ALB
  |-----------------------------|
  v                             v
AZ-A EC2                       AZ-B EC2
+----------------------+       +----------------------+
| K3s Server           |       | K3s Agent            |
| Prometheus / Grafana |       | Spring API only      |
| Spring API           |       |                      |
+----------+-----------+       +----------+-----------+
           \                         /
            \                       /
             +---------+-----------+
                       v
                  RDS MySQL
             Primary / Preferred AZ-A
```

ALB는 각 EC2의 K3s `NodePort`를 Target Group에 등록합니다. 별도의 Ingress Controller는 사용하지 않고, ALB health check가 각 AZ의 NodePort를 확인해 정상 대상에만 트래픽을 전달합니다. AZ별 장애를 정확히 감지하려면 Application Service에 `externalTrafficPolicy: Local`을 적용하고, 각 EC2에 로컬 Application Pod가 배치되어야 합니다. 그렇지 않으면 B EC2의 NodePort가 A Pod로 요청을 전달해 B 장애가 가려질 수 있습니다.

#### AZ-B Failure Scenario

```text
Normal:  Client -> ALB -> AZ-A or AZ-B EC2 NodePort -> Application -> RDS MySQL
Failure: AZ-B outage -> ALB health check fails -> AZ-A NodePort serves traffic
Data:    AZ-A/B Application -> RDS MySQL (primary / preferred AZ-A)
```

- AZ-B EC2 또는 애플리케이션 Pod를 중단해 장애를 주입합니다.
- ALB Target Group이 AZ-A와 AZ-B의 NodePort를 대상으로 가지는지 확인합니다.
- ALB health check가 AZ-B 대상을 제외하는지 확인합니다.
- Application Service에 `externalTrafficPolicy: Local`이 적용됐는지 확인합니다.
- AZ-A와 AZ-B에 각각 로컬 Application Pod가 배치됐는지 확인합니다.
- 트래픽이 AZ-A 애플리케이션으로 전환되는지 확인합니다.
- AZ-A 애플리케이션이 RDS MySQL에 계속 연결되는지 확인합니다.
- 응답 성공률, failover 소요 시간, 데이터 정합성을 기록합니다.

이번 단계의 DR 대상은 `AZ-B EC2 및 애플리케이션 장애`입니다. RDS MySQL은 정상적으로 동작한다는 전제하에 DB 장애는 범위에서 제외합니다. RDS endpoint는 애플리케이션이 공통으로 사용하며, 장애 시에도 AZ-A 애플리케이션이 같은 RDS에 연결합니다.

### Architecture Decisions

| 단계 | 배포 대상 | 운영 검증 |
| --- | --- | --- |
| 0단계 | Docker Compose의 Spring API와 MySQL | API 실행 및 로컬 테스트 |
| 1단계 | `t3.small` 1대의 단일 노드 K3s와 API / MySQL | 클러스터 연결 및 서비스 접근 |
| 2단계 | GitHub Actions, Docker Hub, Metrics Server, 경량 Prometheus / Grafana | 자동 배포와 HPA/DR을 위한 관측 |
| 3단계 | EC2 2대, ALB, NodePort, K3s 관리(AZ-A), 애플리케이션(AZ-A/B), RDS MySQL | AZ-B 단절 후 AZ-A failover, RDS 정상 동작 전제 |

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
DB_ROOT_PASSWORD=<your-root-password>
DB_NAME=opspilot
DB_USERNAME=<your-db-username>
DB_PASSWORD=<your-db-password>
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

### User CRUD 및 장애 테스트 API

기존 `/hello/`와 controller → service → repository 구조를 유지합니다. User는 `id`, `name`을 가지며 기존 `HelloRepository`를 재사용하고 MySQL `users` 테이블에 저장합니다. 이름은 공백이 아닌 1~100자입니다.

| Method | Endpoint | 동작 |
| --- | --- | --- |
| GET | `/hello/` | 기존 hello 응답 |
| POST | `/api/users` | `{"name":"Alice"}` 생성, 201 및 Location |
| GET | `/api/users` | ID 순 전체 조회 |
| GET | `/api/users/{id}` | 단건 조회 |
| PUT | `/api/users/{id}` | `{"name":"Bob"}` 이름 변경 |
| DELETE | `/api/users/{id}` | 삭제, 204 |
| POST | `/api/test/latency?durationMs=1000` | 요청 스레드 대기 |
| POST | `/api/test/cpu?durationMs=1000` | 요청 스레드에서 CPU 연산 |
| POST | `/api/test/memory?sizeMb=32&durationMs=1000` | 메모리 할당·쓰기 후 지정 시간 보유 |
| POST | `/api/test/error` | 의도적인 HTTP 500 |

없는 User는 404, 유효하지 않은 본문·파라미터는 400을 반환합니다. 장애 API는 기본 비활성화(404)이며 `.env`에 `FAULT_TESTS_ENABLED=true`를 추가하면 Compose에서 활성화됩니다. 호스트에서 실행할 때는 환경변수로 전달합니다.

```bash
FAULT_TESTS_ENABLED=true ./gradlew bootRun
curl -i -X POST http://localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"Alice"}'
curl -X POST 'http://localhost:8080/api/test/latency?durationMs=1000'
curl -X POST 'http://localhost:8080/api/test/cpu?durationMs=5000'
curl -X POST 'http://localhost:8080/api/test/memory?sizeMb=64&durationMs=10000'
curl -i -X POST http://localhost:8080/api/test/error
```

`durationMs`는 1~30,000ms(기본 1,000ms), `sizeMb`는 1~128MiB(기본 32MiB)입니다. CPU·메모리 테스트는 인스턴스당 합계 한 요청만 실행하며 중복 요청은 429를 반환합니다. CPU는 한 스레드를 사용하고 latency는 동시 요청이 가능합니다. 메모리는 요청 종료 후 GC 대상이 되므로 힙 사용량이 즉시 내려가지는 않습니다. 장애 API를 켠 환경은 테스트용 접근으로 제한합니다.

MySQL 연결은 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 또는 Spring datasource 환경변수로 설정합니다. DB 이름·사용자명·비밀번호에는 기본값이 없으며, 필수 설정이 누락되면 시작에 실패합니다. Compose는 필수 DB 환경변수가 누락되거나 비어 있으면 실행 전에 오류를 반환합니다. `SERVER_PORT`의 기본값은 8080입니다. 로컬/Compose의 테이블 초기화는 `DDL_AUTO=update`이며, 운영 스키마를 별도 관리할 때는 `DDL_AUTO=validate`를 사용합니다. 호스트의 `bootRun`은 `.env`를 자동으로 읽지 않으므로 필요한 환경변수를 직접 설정합니다.

### Actuator 및 Prometheus

| Endpoint | 용도 |
| --- | --- |
| `/actuator/health` | DB를 포함한 상태 확인 |
| `/actuator/health/liveness` | 프로세스 생존 상태 |
| `/actuator/health/readiness` | 요청 수신 준비 상태 |
| `/actuator/info` | 애플리케이션 정보(현재 빈 객체) |
| `/actuator/metrics` | 메트릭 목록 및 `/metrics/{name}` 조회 |
| `/actuator/prometheus` | Prometheus scrape |

[Spring Boot 공식 metrics 문서](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)에 따른 Actuator와 Micrometer Prometheus registry를 사용합니다. JVM 메모리, CPU 및 HTTP 요청 메트릭을 노출하고 HTTP 응답 시간 histogram을 활성화합니다. 기본 readiness는 DB 상태를 포함하지 않습니다.

```yaml
# Prometheus 설정 예시: Compose 네트워크 내부에서는 app:8080 사용
scrape_configs:
  - job_name: opspilot
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['localhost:8080']
```

`./gradlew test`는 별도의 `test` 프로필과 H2 MySQL 호환 모드로 실행되어 외부 MySQL 없이 CRUD 전체 흐름, 입력 오류, 장애 API, 비활성화 설정, Actuator를 검증합니다. 실제 MySQL 호환성 검증은 `docker compose up --build` 후 위 CRUD 요청으로 수행합니다.

## Stage 1. Infrastructure and K3s

`t3.small` 인스턴스 1개를 기준으로 최소 구성을 먼저 완성합니다. 한 노드 안에서 K3s, Spring API, MySQL을 실행해 애플리케이션 배포와 기본 운영 방식을 검증합니다.

### Terraform

- AWS 리소스를 코드로 관리
- `t3.small` EC2 인스턴스 1개 구성
- 네트워크와 보안 그룹 구성
- 환경별 인프라 변경 이력 관리
- `terraform plan`과 `terraform apply`를 통한 재현 가능한 변경

### K3s

- 경량 Kubernetes 클러스터 구성
- 애플리케이션 Deployment와 Service 배포
- Docker Hub 이미지를 이용한 애플리케이션 실행
- 같은 클러스터 안에서 API와 MySQL 실행
- AWS 인스턴스와 K3s 클러스터 연결 검증

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
- [ ] 단일 `t3.small` 인스턴스에 K3s가 정상적으로 구성됨
- [ ] Docker 이미지로 Spring API와 MySQL이 같은 클러스터에 배포됨
- [ ] 외부 요청이 애플리케이션 Service까지 도달함
- [ ] 장애 발생 시 Kubernetes 리소스 상태와 로그로 원인을 확인할 수 있음

## Stage 2. CI/CD and Observability

코드 변경이 테스트, 이미지 빌드, Docker Hub push, K3s 배포로 이어지는 CI/CD를 구축합니다. HPA와 DR 검증의 전제인 모니터링은 제외하지 않고, 단일 인스턴스에서도 동작할 수 있도록 경량 구성으로 운영합니다.

### CI/CD

- GitHub Actions로 테스트 자동화
- Docker 이미지 빌드 및 Docker Hub push
- 커밋 또는 태그 기반 이미지 버전 관리
- GitHub Actions에서 K3s 배포 단계 자동화
- 배포 실패 시 원인 확인과 롤백 절차 검증
- Jenkins는 별도 서버 비용과 운영 부담을 고려해 초기 구성에서 사용하지 않음

### CD Strategy

```text
GitHub Actions
     |-- Test
     |-- Build Docker Image
     |-- Push Image to Docker Hub
     `-- Deploy with kubectl / SSH
                                   |
                                   v
                         K3s Cluster
```

현재 규모에서는 Jenkins를 별도로 띄우기보다 GitHub Actions를 CI/CD에 함께 사용합니다. Jenkins는 파이프라인이 복잡해지거나 self-hosted 실행 환경과 세밀한 권한 관리가 필요해질 때 검토합니다.

### Monitoring and Logging

- Metrics Server로 HPA에 필요한 CPU / 메모리 리소스 메트릭 수집
- 경량 Prometheus로 애플리케이션과 노드 메트릭 수집
- Grafana로 핵심 지표 중심의 대시보드 구성
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
| CI/CD | GitHub Actions |
| Image Registry | Docker Hub |
| HPA Metrics | Kubernetes Metrics Server |
| Metrics | Lightweight Prometheus |
| Dashboard | Lightweight Grafana |
| Logging | Kubernetes / Container Logging |
| Deployment | K3s, Kubernetes |

### Completion Criteria

- [ ] GitHub Actions에서 테스트가 자동으로 실행됨
- [ ] Docker 이미지가 커밋 또는 태그 기준으로 Docker Hub에 등록됨
- [ ] GitHub Actions가 새 이미지를 K3s 환경에 자동 배포함
- [ ] 배포 성공 여부와 롤백 절차를 확인할 수 있음
- [ ] Metrics Server에서 HPA용 CPU / 메모리 메트릭을 수집함
- [ ] 경량 Prometheus에서 애플리케이션과 Kubernetes 메트릭을 수집함
- [ ] Grafana에서 요청 수, 응답 시간, 에러율, CPU, 메모리를 확인함
- [ ] 주요 장애 상황에 대한 로그와 알림 기준이 정의됨

## Stage 3. Advanced Operations

EC2 인스턴스를 AZ-A와 AZ-B에 각각 1대씩 추가하고, 데이터베이스는 AWS RDS MySQL로 분리합니다. AZ-A에는 K3s 관리 노드, 모니터링, 애플리케이션을 배치하고 RDS MySQL의 기본 배치 영역도 AZ-A로 구성합니다. AZ-B에는 애플리케이션만 배치합니다. 이 단계에서는 AZ-B를 단절시킨 뒤 AZ-A 애플리케이션으로 failover되는지 검증합니다.

### HPA Validation

- CPU 및 메모리 기반 Horizontal Pod Autoscaler 구성
- 부하를 주어 Pod 증가와 축소 동작 검증
- 확장까지 걸리는 시간과 최대 Pod 수 측정
- 리소스 requests / limits와 HPA 동작의 관계 확인

### Disaster Recovery Test

- 애플리케이션 장애 및 Pod 삭제 시나리오
- 노드 장애 및 서비스 재배포 검증
- AZ-A EC2에 K3s 관리 노드, 모니터링, 애플리케이션 구성
- AZ-A에 RDS MySQL의 기본 배치 영역 구성
- AZ-B EC2에는 애플리케이션만 구성
- AZ-B 인스턴스 단절 후 AZ-A 애플리케이션으로 failover
- RDS MySQL이 정상 동작하는 상태에서 애플리케이션 연결 유지
- failover 이후 애플리케이션 응답 성공률과 복구 시간 측정

### Network Flow

- ALB Target Group은 AZ-A와 AZ-B의 EC2 인스턴스를 대상으로 함
- ALB는 각 EC2의 K3s NodePort로 HTTP 요청과 health check를 전달함
- NodePort는 Kubernetes Service로 요청을 전달하고, Service가 로컬 Application Pod로 연결함
- RDS 장애와 DB endpoint failover는 이번 DR 범위에 포함하지 않음

### Tech Stack

| 영역 | 기술 |
| --- | --- |
| Scaling | Kubernetes HPA |
| Load Test | 부하 테스트 도구 |
| Recovery | Application Failover |
| Observability | Prometheus, Grafana, Logging |
| Infrastructure | Terraform, AWS, K3s, EC2, AZ |
| Database | AWS RDS for MySQL |
| Traffic | ALB, Health Check |

### Completion Criteria

- [ ] 부하 증가에 따라 HPA가 Pod를 확장함
- [ ] 부하 감소 후 Pod가 설정된 최소 개수까지 축소됨
- [ ] CPU / 메모리 requests와 limits가 HPA 기준에 맞게 설정됨
- [ ] 애플리케이션 Pod 삭제 후 서비스가 자동으로 복구됨
- [ ] 노드 장애 또는 재배포 후 서비스가 정상화됨
- [ ] AZ-A와 AZ-B에 EC2 인스턴스가 각각 1대씩 구성됨
- [ ] AZ-A에 K3s 관리, 모니터링, 애플리케이션이 구성됨
- [ ] AZ-A에 RDS MySQL의 기본 배치 영역이 구성됨
- [ ] AZ-B에 애플리케이션만 구성됨
- [ ] ALB가 AZ-A와 AZ-B EC2의 NodePort로 트래픽을 전달함
- [ ] Load Balancer health check가 비정상 AZ의 대상을 제외함
- [ ] AZ-B 단절 후 AZ-A 애플리케이션으로 failover됨
- [ ] RDS MySQL이 정상 동작하는 상태에서 애플리케이션이 DB에 연결됨
- [ ] AZ-B 단절 후 AZ-A 애플리케이션으로 failover됨
- [ ] failover 소요 시간과 응답 성공률을 기록함

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
