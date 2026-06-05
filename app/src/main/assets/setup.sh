#!/data/data/com.neonide.studio/files/usr/bin/env bash

set -e

export DEBIAN_FRONTEND=noninteractive

echo "--- [1/5] Updating system packages ---"

dpkg --configure -a
apt update
apt upgrade -y --no-install-recommends

echo "--- [2/5] Installing packages ---"

apt install -y --no-install-recommends \
    openjdk-21 \
    unzip \
    jq 

echo "--- [3/5] Configuring Environment Variables ---"

cat > $HOME/.bashrc <<'EOF'
export JAVA_HOME=$PREFIX/lib/jvm/java-21-openjdk
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export ANDROID_USER_HOME=$HOME/.android

export PATH=$PATH:$JAVA_HOME/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/build-tools/36.0.0

export SSL_CERT_FILE=$PREFIX/etc/tls/cert.pem
EOF

echo "--- [4/5] Preparing up Android SDK Tools ---"

mkdir -p "$HOME/android-sdk"

if [ ! -d "$HOME/android-sdk/build-tools/36.0.0" ]; then

    mkdir ~/.gyp && echo "{'variables':{'android_ndk_path':''}}" > ~/.gyp/include.gypi
    
    echo "Downloading Android build-tools..."
    
    curl -O -L https://github.com/AndroidStudio-App/AndroidSDK-Tools-/releases/download/release-platform-tools-36.0.0-arm64-v8a/AndroidSDK-36.0.0.zip
    unzip -oq AndroidSDK-36.0.0.zip && rm AndroidSDK-36.0.0.zip
    mv -f build-tools platform-tools others $HOME/android-sdk
    
    echo "Android build-tools installed successfully!"
fi

if [ ! -d "$HOME/android-sdk/cmdline-tools/latest/bin" ]; then
    
    echo "Downloading official Android Command Line Tools..."
    
    curl -O -L https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
    unzip -oq commandlinetools-linux-13114758_latest.zip && rm commandlinetools-linux-13114758_latest.zip
    
    mkdir -p "$HOME/android-sdk/cmdline-tools"
    mv cmdline-tools "$HOME/android-sdk/cmdline-tools/latest"
    
    echo "Command Line Tools installed successfully!"
fi

if [ ! -d "$HOME/android-sdk/ndk/29.0.14206865" ]; then
    
    echo "Downloading Android NDK (Termux compatible)..."
    
    curl -O -L https://github.com/AndroidCSOfficial/acs-build-system/releases/download/v29.0.14033849/android-ndk-r29-aarch64-linux-android.tar.xz
    tar -xJf android-ndk-r29-aarch64-linux-android.tar.xz && rm -f android-ndk-r29-aarch64-linux-android.tar.xz
    mkdir -p $HOME/android-sdk/ndk
    mv android-ndk-r29 $HOME/android-sdk/ndk/29.0.14206865
    
fi

if [ ! -d "$HOME/.config/kotlin-language-server" ]; then
    
    mkdir -p $HOME/.config/kotlin-language-server
    cat > "$HOME/.config/kotlin-language-server/classpath" << 'EOF'
#!/data/data/com.neonide.studio/files/usr/bin/sh

GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
TRANSFORMS="$HOME/.gradle/caches"

ANDROID_JAR="$ANDROID_SDK/platforms/android-35/android.jar"
KOTLIN_STDLIB=$(find "$GRADLE_CACHE" -name "kotlin-stdlib-*.jar" 2>/dev/null | head -1)

ANDROIDX_JARS=$(find "$TRANSFORMS" \( -name "classes.jar" -o -name "*.jar" \) \
    \( -path "*transformed*" -o -path "*modules-2*" \) 2>/dev/null | \
    grep -iE "kotlin-stdlib|kotlin-reflect|kotlinx-coroutines|kotlinx-serialization|\
activity|annotation|core|appcompat|material|constraintlayout|recyclerview|\
fragment|lifecycle|viewmodel|core-ktx|coordinatorlayout|drawerlayout|cardview|\
savedstate|startup|window|transition|vectordrawable|viewpager|\
compose|ui-tooling|ui-graphics|ui-text|ui-unit|ui-geometry|\
runtime|foundation|material3|animation|compiler|snapshot|\
accompanist|coil|hilt-navigation-compose|navigation-compose" | \
    grep -v "sources\.jar\|javadoc\.jar\|-sources-\|-javadoc-" | \
    sort -u)

printf "%s" "$ANDROID_JAR:$KOTLIN_STDLIB"
for jar in $ANDROIDX_JARS; do
    printf ":%s" "$jar"
done
printf "\n"

EOF
fi

create_ndk_metadata() {
    cat > "$HOME/android-sdk/ndk/29.0.14206865/source.properties" << 'EOF'
Pkg.Desc = Android NDK
Pkg.Revision = 29.0.14206865
Pkg.Path = ndk;29.0.14206865
Pkg.UserSrc = false
EOF

    cat > "$HOME/android-sdk/ndk/29.0.14206865/package.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository
    xmlns:ns2="http://schemas.android.com/repository/android/common/02"
    xmlns:ns3="http://schemas.android.com/repository/android/common/01"
    xmlns:ns4="http://schemas.android.com/repository/android/generic/01"
    xmlns:ns5="http://schemas.android.com/repository/android/generic/02"
    xmlns:ns6="http://schemas.android.com/sdk/android/repo/addon2/01"
    xmlns:ns7="http://schemas.android.com/sdk/android/repo/addon2/02"
    xmlns:ns8="http://schemas.android.com/sdk/android/repo/repository2/01"
    xmlns:ns9="http://schemas.android.com/sdk/android/repo/repository2/02"
    xmlns:ns10="http://schemas.android.com/sdk/android/repo/sys-img2/02"
    xmlns:ns11="http://schemas.android.com/sdk/android/repo/sys-img2/01">
    <license id="android-sdk-license" type="text">Terms and Conditions</license>
    <localPackage path="ndk;29.0.14206865" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:genericDetailsType"/>
        <revision>
            <major>29</major>
            <minor>0</minor>
            <micro>14206865</micro>
        </revision>
        <display-name>Android NDK 29.0.14206865</display-name>
        <uses-license ref="android-sdk-license"/>
    </localPackage>
</ns2:repository>
EOF
}

echo "--- [5/5] Finalizing ---"
source $HOME/.bashrc
chmod +x $HOME/.config/kotlin-language-server/classpath
yes | $PREFIX/bin/bash $HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
clear && rm setup.sh