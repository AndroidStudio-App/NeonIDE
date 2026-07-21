# NeonIDE studio
Modern Integrated Development Environment(IDE) for Android That Lets you write code and build Android gradle projects 

***Experimental project,*** Development are slow
## Language Servers
***language servers / more will be implemented***
- [x] Java and kotlin language 
- [x] Yaml language
- [x] Xml language
- [x] Bash language
- [x] Json and Typescript language
- [x] Dart language (To install dart-sdk)
```
apt install -y dart
```
## Adding TextMate Grammar
1. Add the configuration and language grammar from [textmate-grammar](https://github.com/shikijs/textmate-grammars-themes/tree/main/packages/tm-grammars) in to the [texmate/assets](https://github.com/AndroidStudio-App/NeonIDE/tree/main/app/src/main/assets/textmate) folder 
2. Set it up in [languages.json](https://github.com/AndroidStudio-App/NeonIDE/blob/main/app/src/main/assets/textmate/languages.json)
3. Declare the new grammar in [SoraLanguageProvider.kt](https://github.com/AndroidStudio-App/NeonIDE/blob/main/app/src/main/java/com/neonide/studio/app/editor/SoraLanguageProvider.kt#L35-L54)
5. After that we can implement a new language server
____
## Flutter Setup
### Install from source
Read [Flutter.md](https://github.com/AndroidStudio-App/NeonIDE/blob/main/Flutter.md) for manual installation 
### Install from extensions
1. Run SetupDevkit
2. Install Flutter sdk in Extensions
3. Install Required packages
```
apt install -y git which cmake ninja
```
4. Verify Installation
```
flutter doctor -v
```
Run this command If you received permission denied
```
find $PREFIX/opt/flutter/bin -type d -exec chmod 755 {} + && chmod +x $PREFIX/bin/flutter
```
### Create Flutter project
1. create a project
```
flutter create app && cd app
```
2. update build.gradle.kts and local.proerties
```
# android/local.properties
cmake.dir=/data/data/com.neonide.studio/files/usr
```
```
# android/app/build.gradle.kts
android {
    ndkVersion = "29.0.14206865"
}
```
3. Build APK.
```
# debug / if adb connected, flutter run
flutter build apk --debug --target-platform android-arm64
```
```
# release / if adb connected, flutter run --release
flutter build apk --release --target-platform android-arm64
```
____
## Installation
1. Download and install the apk from [GithubRelease](https://github.com/AndroidStudio-App/NeonIDE/releases)
2. Open terminal to install system image or directly install it in SetupDevkit

 ## Useful Tips
 - Panel option for Variant build
<div> 
  <img src="/images/feat/Panel_01.jpg" width="49%" /> 
  <img src="/images/feat/Panel_02.jpg" width="49%" />
</div>

Some module/package doesn't show in AC(Auto Completion) :
- You need to run assembleDebug to produce artifacts for classpath to make AC show

The Editor show's false diagnostic/tooltip
- Same to AC you need to build the app to produce artifacts to fix diagnostic false error
## PreviewTab
<div>
    <img src="/images/feat/markdown_preview.jpg" width="49%"/>
    <img src="/images/feat/xmllayout_preview.jpg" width="49%"/>
</div>

## Screenshots
<div>
  <img src="/images/01.jpg" width="32%"/>
  <img src="/images/02.jpg" width="32%"/>
  <img src="/images/03.jpg" width="32%"/>
</div>
<div>
  <img src="/images/04.jpg" width="32%"/>
  <img src="/images/05.jpg" width="32%"/>
  <img src="/images/06.jpg" width="32%"/>
</div>

## Credits And 3rd part apps
- Code Editor : [sora-editor](https://github.com/Rosemoe/sora-editor)
- Terminal Emulator : [Termux application](https://github.com/termux/termux-app?tab=readme-ov-file)
- File Tree : [bonsai](https://github.com/adrielcafe/bonsai)
- Textmate Grammars : [tm-grammars / tm-themes](https://github.com/shikijs/textmate-grammars-themes)
- Tree Sitter : [android-tree-sitter](https://github.com/AndroidIDEOfficial/android-tree-sitter)
- Flutter Source code [Flutter](https://github.com/flutter/flutter)
- Java Language Server : [Java](https://github.com/georgewfraser/java-language-server)
- Kotlin Language Server : [kotlin](https://github.com/fwcd/kotlin-language-server)
- XML Language Server : [LemMinX](https://github.com/eclipse-lemminx/lemminx)
## Acknowledgements

Thanks to [JetBrains](https://www.jetbrains.com/?from=CodeEditor) and [vscode](https://github.com/microsoft/vscode) for allocating free open-source
licences for IDEs such as [IntelliJ IDEA](https://www.jetbrains.com/idea/?from=CodeEditor) and [Visual Studio Code](https://code.visualstudio.com/)
