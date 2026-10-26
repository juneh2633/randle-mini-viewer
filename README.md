# 랜들 미니 뷰어

[랜들](https://randle.lixx.kr/play)의 숫자를 항상 위에 뜨는 작은 창으로 보여 줍니다. 유튜브를 보거나 다른 작업을 할 때 사용할 수 있습니다.

PC 미니 창에서 자리를 선택하고 리롤·확정·다음 판을 직접 누를 수 있습니다.

Android용 작은 창 앱도 시험판으로 제공합니다. 숫자와 필수 버튼을 작은 창에 표시하며 웹 화면은 펼쳐서 조작합니다. [Android APK 다운로드](https://github.com/juneh2633/randle-mini-viewer/releases/tag/android-v0.3.0) · [설치·사용 안내](android/README.md). PC용 확장 프로그램과 별도 앱으로 설치됩니다.

## 다운로드 및 설치

1. [최신 릴리스](https://github.com/juneh2633/randle-mini-viewer/releases/latest)에서 `randle-mini-viewer-1.1.0.zip`을 받아 압축을 풉니다.
2. Chrome에서 `chrome://extensions` 또는 Edge에서 `edge://extensions`를 엽니다.
3. **개발자 모드 → 압축해제된 확장 프로그램을 로드**를 누릅니다.
4. 압축을 푼 폴더 중 `manifest.json`이 있는 폴더를 선택합니다.
5. 랜들 뽑기 페이지를 열거나 새로고침하고 **항상 위 미니 창 ↗**을 누릅니다.

GitHub의 **Code → Download ZIP**으로 소스를 내려받았다면, 압축 해제 후 **`extension` 폴더**를 선택하세요. Releases의 설치 ZIP은 압축 해제한 폴더에 바로 `manifest.json`이 있습니다.

## 기능

- 확정 전 공개된 자리부터 숫자를 실시간으로 표시합니다. 미공개 자리는 `?`로 표시하고, 앞자리 0도 보존합니다.
- 다른 탭이나 앱을 보면서 미니 창을 옮기거나 크기를 조절할 수 있습니다.
- 사이트의 제한 시간과 인증 필요 상태를 표시합니다.
- 숫자 자리를 눌러 리롤할 자리를 선택할 수 있습니다. 선택한 자리는 노란색으로 표시합니다.
- **선택한 자리 굴리기**, **전체 선택**, **이 숫자로 확정**, **숫자 뽑기 / 다음 판 시작 / 한 판 더 굴리기** 버튼을 사용할 수 있습니다. 실제 게임 상태에 따라 버튼이 켜지거나 꺼집니다.
- 버튼을 한 번 누를 때만 해당 동작을 한 번 실행합니다.
- 원래 랜들 탭을 닫거나 새로고침하면 미니 창도 닫힙니다. 인증은 원래 탭에서 직접 해 주세요.

사이트 자체의 제한 시간은 계속 흐릅니다. 계정 정보나 숫자를 저장하거나 외부 서버에 보내지 않습니다.

데스크톱 Chrome / Edge의 Document Picture-in-Picture 기능을 사용합니다. 모바일이나 브라우저 정책, 운영체제의 전체 화면 처리 방식에 따라 지원되지 않거나 창이 가려질 수 있습니다. 랜들 사이트의 공식 확장 프로그램이나 Chrome 웹 스토어 배포본은 아닙니다.

## 개발

Node.js 18 이상, 패키징에는 PowerShell 7(`pwsh`)이 필요합니다.

```sh
npm ci
npm run check
npm test
npm run package
```

설치 ZIP과 SHA-256 체크섬은 `dist/`에 생성됩니다. 소스 상태로 설치하려면 `extension/` 폴더를 로드하면 됩니다.

## 릴리스

`extension/manifest.json`, `package.json`의 버전과 `RELEASE_NOTES.md`를 갱신한 뒤 버전 태그를 푸시합니다.

```sh
git tag v1.1.0
git push origin v1.1.0
```

GitHub Actions에서 검사 후 설치 ZIP과 체크섬을 Releases에 올립니다. 태그와 manifest 버전이 다르면 릴리스가 중단됩니다.
