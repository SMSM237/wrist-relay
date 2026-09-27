# 릴리스 빌드와 서명

## 현재 상태

R8 축소 릴리스 AAB는 빌드할 수 있지만, 배포용 개인 키는 저장소에 만들거나 보관하지 않습니다. 앱을 상용 배포하려면 복구 가능한 외부 백업 위치를 먼저 정한 뒤 키를 생성해야 합니다.

## 필수 원칙

1. 키스토어 원본과 암호를 저장소, 채팅, APK와 같은 폴더에 두지 않습니다.
2. 암호화된 외부 백업 위치 두 곳에 복제하고 복구 테스트를 마칩니다.
3. Gradle에는 로컬 환경 변수 또는 별도 `keystore.properties` 경로만 전달합니다.
4. 서명 후 `apksigner verify --verbose --print-certs`로 인증서와 서명을 확인합니다.
5. 이전 버전 위에 업데이트 설치해 서명 연속성을 확인합니다.

## Gradle 환경 변수

- `WRIST_RELAY_KEYSTORE`: 키스토어 절대 경로
- `WRIST_RELAY_STORE_PASSWORD`: 키스토어 암호
- `WRIST_RELAY_KEY_ALIAS`: 키 별칭
- `WRIST_RELAY_KEY_PASSWORD`: 키 암호

네 값이 모두 있을 때만 Gradle이 릴리스 산출물에 서명합니다. 일부만 설정되면 빌드는 즉시 실패합니다.

## 검증 명령

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug bundleRelease
```

키 생성과 최종 서명은 외부 백업 목적지가 확정된 뒤 수행합니다. 디버그 APK는 개발 검증 전용이며 상용 배포에 사용하면 안 됩니다.
