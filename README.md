# OpsPilot

Spring API와 운영 플랫폼을 함께 구축하며, 서비스 개발부터 배포·관측·장애 대응·비용 최적화까지 운영 전 과정을 실험하는 프로젝트입니다.

## Project Purpose

애플리케이션 개발과 플랫폼 운영을 분리하지 않고 함께 설계합니다.

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

## Tech Stack

- Java 17
- Spring Boot
- Spring Web MVC
- Spring Data JPA
- MySQL
- Docker
- Terraform
- K3s
- GitHub Actions
- Docker Hub
- Prometheus / Grafana

## Local Development

### Requirements

- Java 17+
- Docker and Docker Compose

### Run

```bash
./gradlew bootRun
```

### Test

```bash
./gradlew test
```

## Direction

- 애플리케이션 API와 데이터 계층을 안정적으로 구현
- 반복 가능한 인프라와 배포 파이프라인 구성
- 메트릭, 로그, 알림을 통한 운영 가시성 확보
- 부하와 장애 상황에서의 복구 능력 검증
- 리소스 사용량과 운영 비용 최적화
