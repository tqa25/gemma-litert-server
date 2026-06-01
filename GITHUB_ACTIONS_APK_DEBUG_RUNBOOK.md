# GitHub Actions APK Build Debug Runbook

Tai lieu nay ghi lai cach debug va fix loi khi build file APK bang GitHub Actions. Muc tieu la giam lap lai loi cu, tiet kiem thoi gian/token, va tranh sua code chi de build xanh nhung lam hong muc tieu app.

Tai lieu nay khong khoa cung vao version app cu the. Khi version Android Gradle Plugin, LiteRT-LM, JDK, SDK thay doi thi van dung logic debug ben duoi.

## 1. Nguyen tac bat buoc truoc khi fix

Truoc moi lan sua loi build, doc lai muc tieu app:

```text
Android backend app
  -> foreground service
  -> local HTTP API
  -> GET /health
  -> POST /generate
  -> prompt + image_base64
  -> LiteRT-LM Android backend
  -> JSON response + timing
```

Khong duoc fix bang cach pha muc tieu, vi du:

```text
- Xoa LiteRT runner de build pass.
- Xoa /generate image support de build pass.
- Bien Android app thanh JVM server.
- Xoa foreground service neu muc tieu can backend chay nen.
- Bo upload APK artifact khoi workflow de run xanh gia.
```

Fix dung la:

```text
- Giu contract API.
- Giu duong LiteRT backend.
- Giu APK build artifact.
- Giu service/backend app architecture.
```

## 2. Workflow debug chuan

Moi lan build fail, lam theo thu tu:

```text
1. Xac dinh run id moi nhat.
2. Lay log job fail.
3. Tim dong loi dau tien co y nghia.
4. Phan loai loi.
5. Sua toi thieu.
6. Commit/push.
7. Watch run moi.
8. Chi tiep tuc neu run fail voi loi moi.
```

Lenh hay dung:

```bash
gh run list --workflow android-backend-apk.yml --branch docs/vietnamese-guide-android-plan --limit 5
gh run watch <RUN_ID> --exit-status
gh run view <RUN_ID> --job <JOB_ID> --log
```

Lay phan loi nhanh:

```bash
gh run view <RUN_ID> --job <JOB_ID> --log \
  | grep -A80 -B20 -E 'FAILED|error:|What went wrong|Execution failed|cannot find symbol|constructor|AAPT|Manifest merger'
```

## 3. Cach doc log dung

Dung doc toan bo log tu dau den cuoi neu log dai. Hay tim:

```text
- Task nao fail?
- Loi dau tien la gi?
- Loi nam o infrastructure, dependency, compile, manifest, packaging, hay artifact?
```

Vi du task fail:

```text
:android-backend:compileDebugJavaWithJavac FAILED
```

Nghia la loi Java source/API, khong phai workflow.

Vi du task fail:

```text
:android-backend:processDebugResources FAILED
AAPT2 Daemon startup failed
```

Can xem no la loi local architecture hay CI. Tren Oracle ARM, AAPT2 x86_64 co the fail local, nhung GitHub Actions x86_64 build duoc.

## 4. Phan loai loi va cach fix

### 4.1 Loi CI environment

Dau hieu:

```text
Value '/usr/lib/jvm/java-21-openjdk-arm64' given for org.gradle.java.home is invalid
```

Nguyen nhan:

```text
Repo hard-code Java path cua may local ARM. GitHub Actions dung duong khac.
```

Fix dung:

```text
- Khong commit org.gradle.java.home hard-code.
- De GitHub Actions setup-java set JAVA_HOME.
- Neu can local override, dung file local khong commit hoac env var.
```

Khong nen:

```text
- Sua workflow thanh path hard-code moi cho GitHub.
- Doi JDK xuong version cu neu dependency can Java moi.
```

### 4.2 Loi local Oracle ARM nhung CI co the pass

Dau hieu local:

```text
AAPT2 ... x86_64-binfmt-P: Could not open '/lib64/ld-linux-x86-64.so.2'
```

Nguyen nhan:

```text
Android Gradle Plugin dung AAPT2 Linux x86_64, Oracle VM la ARM64.
```

Logic xu ly:

```text
- Khong ket luan code sai.
- Push len GitHub Actions x86_64 de build APK that.
- Neu GitHub Actions cung fail AAPT/resource thi moi fix code/resource.
```

Khong nen:

```text
- Xoa Android resources/manifest de local ARM build qua.
- Doi sang non-Android module chi de local pass.
```

### 4.3 Loi dependency/API mismatch

Dau hieu:

```text
constructor EngineConfig in class EngineConfig cannot be applied to given types
required: ...
found: ...
```

Nguyen nhan:

```text
LiteRT-LM Android va LiteRT-LM JVM co constructor/API khac nhau.
```

Fix dung:

```text
1. Inspect artifact dang build.
2. Xem signature public.
3. Sua code theo API that.
```

Lenh inspect AAR/JAR:

```bash
find ~/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litertlm/litertlm-android -name '*.aar'
rm -rf /tmp/litertlm-android-aar
mkdir -p /tmp/litertlm-android-aar
unzip -q <PATH_TO_AAR> -d /tmp/litertlm-android-aar
javap -classpath /tmp/litertlm-android-aar/classes.jar com.google.ai.edge.litertlm.EngineConfig
javap -classpath /tmp/litertlm-android-aar/classes.jar com.google.ai.edge.litertlm.Conversation
javap -classpath /tmp/litertlm-android-aar/classes.jar com.google.ai.edge.litertlm.Content
```

Quy tac:

```text
- Khong doan API tu JVM docs neu dang build Android artifact.
- Khong doan API tu Android docs neu dang build JVM artifact.
- Artifact nao compile thi inspect artifact do.
```

### 4.4 Loi manifest

Dau hieu:

```text
Manifest merger failed
uses-permission ...
foregroundServiceType ...
exported ...
```

Checklist:

```text
- Activity co android:exported="true" neu co intent-filter MAIN/LAUNCHER.
- Service co android:exported="false".
- Foreground service permission du voi target SDK.
- Neu foregroundServiceType="dataSync" thi co permission tuong ung.
```

Fix dung:

```text
- Sua manifest theo yeu cau Android platform.
- Khong xoa service neu app can backend chay nen.
```

### 4.5 Loi resource/AAPT tren CI

Dau hieu:

```text
processDebugResources FAILED
resource ... not found
style ... not found
```

Checklist:

```text
- strings.xml co app_name.
- styles.xml co theme dung.
- Manifest reference dung resource.
- compileSdk du moi voi dependency.
```

Fix dung:

```text
- Them resource thieu.
- Sua ten resource sai.
- Tang compileSdk neu dependency can.
```

### 4.6 Loi upload artifact

Dau hieu:

```text
Upload APK artifact failed
No files were found with the provided path
```

Checklist:

```text
- Build task co that su tao APK khong?
- Path artifact dung khong?
- Module name dung khong?
```

Path dung cho debug APK hien tai:

```text
android-backend/build/outputs/apk/debug/*.apk
```

Khong nen:

```text
- Doi if-no-files-found thanh ignore de workflow xanh gia.
```

## 5. Vong lap fix an toan

Moi lan fix dung format nay trong dau:

```text
Loi: <mot cau>
Loai loi: environment / dependency API / manifest / resource / code compile / artifact
Muc tieu app co bi anh huong khong?
Fix toi thieu la gi?
Sau fix can test bang lenh nao?
```

Vi du:

```text
Loi: EngineConfig Android can 7 args, code dang goi 5 args.
Loai loi: dependency API mismatch.
Muc tieu app: khong doi, van can LiteRT backend.
Fix: inspect AAR va goi constructor dung 7 args.
Test: GitHub Actions :android-backend:assembleDebug.
```

## 6. Tranh sua lap lai cung mot loi

Ghi nho cac loi da gap:

### Loi da gap 1: hard-code org.gradle.java.home

```text
Khong commit org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-arm64
```

Ly do:

```text
Duong nay chi dung tren Oracle ARM, sai tren GitHub Actions x86_64.
```

Cach dung dung:

```text
- Local: dung JAVA_HOME hoac file local khong commit.
- CI: actions/setup-java set JAVA_HOME.
```

### Loi da gap 2: EngineConfig Android != EngineConfig JVM

```text
LiteRT-LM Android EngineConfig 0.12.0 required:
String, Backend, Backend, Backend, Integer, Integer, String
```

Cach tranh lap lai:

```text
- Khi update LiteRT-LM, inspect AAR bang javap truoc khi sua code.
- Khong copy y nguyen JVM constructor sang Android.
```

### Loi da gap 3: AAPT2 local fail tren Oracle ARM

```text
Local ARM fail khong dong nghia CI fail.
```

Cach tranh lap lai:

```text
- Dung GitHub Actions x86_64 lam build APK source of truth.
- Local ARM chi dung de edit code/JVM server, khong ep Android APK local pass bang moi gia.
```

## 7. Checklist truoc khi push fix

Truoc moi lan `git push`, kiem tra:

```bash
git status --short
git diff --cached --stat
```

Dam bao:

```text
- Khong add models/*.litertlm
- Khong add logs/
- Khong add build/
- Khong add .gradle/ hoac .kotlin/
- Khong xoa LiteRT runner de build pass
- Khong xoa workflow upload artifact
```

## 8. Checklist sau khi push

```bash
gh run list --workflow android-backend-apk.yml --branch docs/vietnamese-guide-android-plan --limit 3
gh run watch <RUN_ID> --exit-status
```

Neu pass:

```bash
gh run download <RUN_ID> --dir /tmp/gemma-android-apk-artifact
find /tmp/gemma-android-apk-artifact -type f -name '*.apk' -printf '%p %s bytes\n'
```

Run pass dung phai co:

```text
- Build debug APK: success
- Upload APK artifact: success
- Artifact co file .apk
```

## 9. Khi nao moi coi la build thanh cong

Khong chi nhin workflow green. Phai co du 3 dieu kien:

```text
1. GitHub Actions conclusion = success.
2. Task :android-backend:assembleDebug pass.
3. Artifact upload co file APK.
```

## 10. Viec tiep theo sau build APK thanh cong

Sau khi co APK:

```text
1. Cai APK len ROG Phone 6.
2. Copy model vao /sdcard/Models/gemma-4-E4B-it.litertlm.
3. Start Mock Server truoc.
4. Test /health tu Termux.
5. Start LiteRT Server.
6. Test /generate voi anh nho.
7. Ghi benchmark thuc te.
```

Lenh Termux/ADB:

```bash
curl http://127.0.0.1:8765/health
adb forward tcp:8765 tcp:8765
```
