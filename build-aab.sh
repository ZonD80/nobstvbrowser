#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

KEYSTORE="$HOME/googlePlayKeys.jks"
KEYSTORE_PROPS="$ROOT_DIR/keystore.properties"
GRADLE_FILE="$ROOT_DIR/app/build.gradle.kts"
OUTPUT="$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"

die() {
    echo "error: $*" >&2
    exit 1
}

find_android_studio_jdk() {
    local candidate
    for candidate in \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        /Applications/Android\ Studio*.app/Contents/jbr/Contents/Home; do
        if [[ -x "$candidate/bin/java" ]]; then
            echo "$candidate"
            return 0
        fi
    done
    return 1
}

setup_java() {
    local jdk
    if jdk="$(find_android_studio_jdk)"; then
        :
    elif [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
        jdk="$JAVA_HOME"
    else
        die "Android Studio JDK not found; install Android Studio or set JAVA_HOME to JDK 17+"
    fi

    export JAVA_HOME="$jdk"
    export PATH="$JAVA_HOME/bin:$PATH"

    local version
    version="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n1)"
    echo "Using Java: $JAVA_HOME"
    echo "  $version"
    echo
}

bump_version_code() {
    local current next
    current="$(grep -E '^[[:space:]]*versionCode[[:space:]]*=' "$GRADLE_FILE" \
        | head -n1 \
        | sed -E 's/.*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/')"

    if [[ ! "$current" =~ ^[0-9]+$ ]]; then
        die "versionCode not found in $GRADLE_FILE"
    fi

    next=$((current + 1))
    sed -i '' -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)${current}/\\1${next}/" "$GRADLE_FILE"

    if ! grep -qE "^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*${next}[[:space:]]*$" "$GRADLE_FILE"; then
        die "failed to update versionCode in $GRADLE_FILE"
    fi

    echo "Bumped versionCode: $current -> $next"
    echo
}

setup_java

if [[ ! -f "$KEYSTORE" ]]; then
    die "keystore not found at $KEYSTORE"
fi

if [[ ! -f "$KEYSTORE_PROPS" ]]; then
    die "keystore.properties not found at $KEYSTORE_PROPS"
fi

for key in storePassword keyPassword keyAlias; do
    if ! grep -q "^${key}=" "$KEYSTORE_PROPS"; then
        die "keystore.properties is missing '$key'"
    fi
done

bump_version_code

echo "Building signed release app bundle..."
./gradlew bundleRelease

if [[ ! -f "$OUTPUT" ]]; then
    die "build finished but bundle not found at $OUTPUT"
fi

echo
echo "App bundle ready:"
echo "  $OUTPUT"
echo
ls -lh "$OUTPUT"
