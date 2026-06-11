# NeonIDE studio
Modern Integrated Development Environment(IDE) for Android That Lets you write code and build Android gradle projects 

***! local properties*** will be overwrite/written for ndk path

***Experimental project,*** Development are slow
## Language Servers
***language servers / more will be implemented***
- [x] Java and kotlin language 
- [x] Yaml language
- [x] Xml language
- [x] Bash language
- [x] Json and Typescript language
- [x] Dart language (To install dart-sdk)

      apt install -y dart

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
- Java Language Server : [Java](https://github.com/georgewfraser/java-language-server)
- Kotlin Language Server : [kotlin](https://github.com/fwcd/kotlin-language-server)
- XML Language Server : [LemMinX](https://github.com/eclipse-lemminx/lemminx)
## Acknowledgements

Thanks to [JetBrains](https://www.jetbrains.com/?from=CodeEditor) and [vscode](https://github.com/microsoft/vscode) for allocating free open-source
licences for IDEs such as [IntelliJ IDEA](https://www.jetbrains.com/idea/?from=CodeEditor) and [Visual Studio Code](https://code.visualstudio.com/)
