# experiments

실험별 프롬프트, AI 최초 산출물, 측정 리포트, 결과를 보존하는 디렉터리입니다.
절차와 규칙은 [docs/EXPERIMENT_PROTOCOL.md](../docs/EXPERIMENT_PROTOCOL.md)를 따릅니다.

- 새 실험: `_TEMPLATE/`을 `EXP-NNN-<slug>/`로 복사해서 사용합니다.
- `prompt.md`와 `ai-original/`은 생성 후 수정하지 않습니다.
- 이 디렉터리는 Gradle 빌드 대상이 아닙니다.

| ID | 제목 | 상태 | 태그 |
|----|------|------|------|
| EXP-001-grade-fee-baseline | 등급·수수료 계산 최초 구현 (A·B 비교 대상) | 진행 — 테스트 미작성 | `exp-001-ai-original` |
