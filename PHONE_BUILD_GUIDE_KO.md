# 휴대폰만으로 LingoFlow APK 만들기

이 프로젝트에는 GitHub Actions 빌드 설정이 추가되어 있습니다. Android Studio 없이 GitHub의 클라우드 컴퓨터에서 APK를 빌드하는 방식입니다.

## 준비물
- GitHub 계정
- 휴대폰 브라우저
- 압축을 풀 수 있는 파일 앱

## 진행 순서
1. 이 ZIP을 휴대폰에 내려받고 압축을 풉니다.
2. 휴대폰 브라우저에서 https://github.com 에 로그인합니다.
3. 새 저장소(New repository)를 만듭니다. 이름은 `LingoFlow`로 하고 Public 또는 Private 중 선택합니다.
4. 저장소 화면에서 `Add file` → `Upload files`를 선택합니다.
5. 압축을 푼 프로젝트 안의 파일과 폴더를 저장소에 올립니다. 숨김 폴더 `.github`도 반드시 포함해야 합니다. 폴더 업로드가 모바일 브라우저에서 지원되지 않으면 GitHub Codespaces 또는 Android용 Git 클라이언트를 이용해야 할 수 있습니다.
6. 업로드한 파일을 커밋합니다.
7. 저장소의 `Actions` 탭 → `Build LingoFlow APK` → `Run workflow`를 누릅니다.
8. 빌드가 끝나면 실행 기록 아래 `Artifacts`에서 `LingoFlow-debug-APK`를 눌러 ZIP을 받습니다.
9. 받은 ZIP을 압축 해제하고 `app-debug.apk`를 눌러 설치합니다. Android가 출처를 알 수 없는 앱 설치 허용을 요청하면, GitHub에서 받은 파일임을 확인한 뒤 브라우저/파일 앱에 설치 권한을 허용합니다.

## 알아둘 점
- 이 빌드는 디버그 APK입니다. 개인 테스트용이며 Play 스토어 배포용 서명 APK가 아닙니다.
- 클라우드 빌드가 성공해도 실제 갤럭시에서 화면 캡처·오버레이·OCR·번역이 모두 정상 작동하는지는 별도로 테스트해야 합니다.
- 앱은 화면 캡처 권한을 사용하므로 캡처를 시작할 때 Android 시스템의 허용 창을 확인하고, 민감한 화면에서는 실행하지 마세요.
- 저장소 업로드가 막히거나 빌드 오류가 나면 오류 화면을 보내 주세요. 오류를 확인해 수정할 수 있습니다.
