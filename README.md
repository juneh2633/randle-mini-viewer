# 랜들 미니 뷰어

[랜들](https://randle.lixx.kr/play)의 숫자를 항상 위에 뜨는 작은 창으로 보여 줍니다. 유튜브를 보거나 다른 작업을 할 때 사용할 수 있습니다.

게임은 원래 랜들 탭에서 직접 진행합니다. 이 확장 프로그램은 숫자와 상태만 표시합니다.

## 다운로드 및 설치

1. [최신 릴리스](https://github.com/juneh2633/randle-mini-viewer/releases/latest)에서 `randle-mini-viewer-1.0.0.zip`을 받아 압축을 풉니다.
2. Chrome에서 `chrome://extensions` 또는 Edge에서 `edge://extensions`를 엽니다.
3. **개발자 모드 → 압축해제된 확장 프로그램을 로드**를 누릅니다.
4. 압축을 푼 폴더 중 `manifest.json`이 있는 폴더를 선택합니다.
5. 랜들 뽑기 페이지를 열거나 새로고침하고 **항상 위 미니 창 ↗**을 누릅니다.

GitHub의 **Code → Download ZIP**으로 소스를 내려받았다면, 압축 해제 후 **`extension` 폴더**를 선택하세요. Releases의 설치 ZIP은 압축 해제한 폴더에 바로 `manifest.json`이 있습니다.

## 기능

- 확정 전 공개된 자리부터 숫자를 실시간으로 표시합니다. 미공개 자리는 `?`로 표시하고, 앞자리 0도 보존합니다.
- 다른 탭이나 앱을 보면서 미니 창을 옮기거나 크기를 조절할 수 있습니다.
- 사이트의 자동 확정 시간과 인증 필요 상태를 표시합니다.
- 원래 랜들 탭을 닫거나 새로고침하면 미니 창도 닫힙니다. 인증이나 게임 조작은 원래 탭에서 직접 해 주세요.

게임 버튼을 자동으로 누르지 않습니다. 계정 정보나 숫자를 저장하거나 외부 서버에 보내지 않습니다.

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
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions에서 검사 후 설치 ZIP과 체크섬을 Releases에 올립니다. 태그와 manifest 버전이 다르면 릴리스가 중단됩니다.
