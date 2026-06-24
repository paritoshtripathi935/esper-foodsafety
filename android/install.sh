#!/usr/bin/env bash
# install.sh — build both APKs and install them to selected emulators/devices
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KIOSK_APK="$SCRIPT_DIR/kiosk-app/build/outputs/apk/debug/kiosk-app-debug.apk"
SIM_APK="$SCRIPT_DIR/probe-simulator/build/outputs/apk/debug/probe-simulator-debug.apk"

# Use Android Studio's embedded JDK if the system java is missing or a stub.
AS_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [[ -x "$AS_JDK/bin/java" ]]; then
    export JAVA_HOME="$AS_JDK"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# ── colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}${BOLD}==>${RESET} $*"; }
success() { echo -e "${GREEN}✓${RESET}  $*"; }
warn()    { echo -e "${YELLOW}⚠${RESET}  $*"; }
error()   { echo -e "${RED}✗${RESET}  $*" >&2; }
die()     { error "$*"; exit 1; }

# ── helpers ───────────────────────────────────────────────────────────────────

check_deps() {
    command -v adb   >/dev/null 2>&1 || die "adb not found — install Android SDK platform-tools and add to PATH"
    command -v java  >/dev/null 2>&1 || die "java not found — required to run Gradle"
}

get_devices() {
    # returns lines of:  serial  state  (model)
    adb devices -l 2>/dev/null \
        | tail -n +2 \
        | grep -v '^$' \
        | awk '{
            serial=$1; state=$2
            model=""
            for(i=3;i<=NF;i++) {
                if ($i ~ /^model:/) { gsub(/^model:/, "", $i); model=$i }
            }
            if (model == "") model="unknown"
            if (state == "device") print serial " (" model ")"
          }'
}

# pick_devices populates the global PICKED_SERIALS array (bash 3.2 compatible)
PICKED_SERIALS=()

pick_devices() {
    local app_name="$1"
    PICKED_SERIALS=()

    echo ""
    info "Connected devices / emulators:"
    local -a lines=()
    while IFS= read -r line; do lines+=("$line"); done < <(get_devices)

    if [[ ${#lines[@]} -eq 0 ]]; then
        die "No devices connected. Start an emulator or plug in a device and retry."
    fi

    local i=1
    for line in "${lines[@]}"; do
        printf "  ${BOLD}%d)${RESET} %s\n" "$i" "$line"
        (( i++ ))
    done

    echo ""
    echo -e "  ${BOLD}0)${RESET} Skip — don't install ${CYAN}${app_name}${RESET}"
    echo ""
    read -rp "$(echo -e "Install ${CYAN}${BOLD}${app_name}${RESET} on which device(s)? [e.g. 1,2 or 0 to skip]: ")" input

    if [[ "$input" == "0" ]]; then
        warn "Skipping $app_name"
        return
    fi

    IFS=',' read -ra chosen <<< "$input"
    local idx serial line
    for idx in "${chosen[@]}"; do
        idx="${idx// /}"   # trim spaces
        if ! [[ "$idx" =~ ^[0-9]+$ ]] || (( idx < 1 || idx > ${#lines[@]} )); then
            error "Invalid selection '$idx' — ignoring"
            continue
        fi
        line="${lines[$((idx-1))]}"
        serial="${line%% *}"   # first token before space
        PICKED_SERIALS+=("$serial")
    done

    if [[ ${#PICKED_SERIALS[@]} -eq 0 ]]; then
        warn "No valid devices selected — skipping $app_name"
    fi
}

install_apk() {
    local apk="$1"
    local serial="$2"
    local label="$3"
    local package="$4"

    echo -e "    Installing ${CYAN}${label}${RESET} → ${BOLD}${serial}${RESET} …"

    # Uninstall first — clears all app data and ensures no stale code remains.
    # Ignore errors (app may not be installed yet on a fresh device).
    adb -s "$serial" shell am force-stop "$package" 2>/dev/null || true
    adb -s "$serial" uninstall "$package" 2>/dev/null || true

    if adb -s "$serial" install "$apk" 2>&1 | grep -qE 'Success|success'; then
        success "Installed $label on $serial"
    else
        error "Failed to install $label on $serial"
        # don't exit — try remaining devices
    fi
}

# ── build ─────────────────────────────────────────────────────────────────────

build_apks() {
    info "Building both APKs (debug) …"
    cd "$SCRIPT_DIR"

    # clean wipes all previous build outputs so the compiler starts from scratch.
    # --no-build-cache prevents pulling cached artifacts from the Gradle cache.
    ./gradlew clean :kiosk-app:assembleDebug :probe-simulator:assembleDebug \
        --no-build-cache --no-daemon 2>&1 \
        | grep -E '(BUILD|FAILURE|error:|warning:)' || true
    # Check the *Gradle* exit code via PIPESTATUS, not the grep exit code.
    local gradle_exit="${PIPESTATUS[0]}"
    (( gradle_exit == 0 )) || die "Gradle build failed (exit $gradle_exit) — not installing stale APKs"

    [[ -f "$KIOSK_APK" ]] || die "Kiosk APK not found after build: $KIOSK_APK"
    [[ -f "$SIM_APK"   ]] || die "Simulator APK not found after build: $SIM_APK"

    success "Build complete"
    echo ""
    echo -e "  Kiosk APK:     ${KIOSK_APK##*/} ($(du -sh "$KIOSK_APK" | cut -f1))"
    echo -e "  Simulator APK: ${SIM_APK##*/} ($(du -sh "$SIM_APK" | cut -f1))"
}

# ── main ──────────────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}SafeTemp — APK installer${RESET}"
echo "───────────────────────────────────────────"

check_deps
build_apks

# Pick devices for kiosk app
pick_devices "Kiosk App (com.esper.foodsafety)"
kiosk_devices=(${PICKED_SERIALS[@]+"${PICKED_SERIALS[@]}"})

# Pick devices for probe simulator
pick_devices "Probe Simulator (com.esper.probesimulator)"
sim_devices=(${PICKED_SERIALS[@]+"${PICKED_SERIALS[@]}"})

# Install
echo ""
info "Installing …"

if [[ ${#kiosk_devices[@]} -gt 0 ]]; then
    for serial in "${kiosk_devices[@]}"; do
        install_apk "$KIOSK_APK" "$serial" "Kiosk App" "com.esper.foodsafety"
    done
else
    warn "Kiosk App: no devices selected, skipped"
fi

if [[ ${#sim_devices[@]} -gt 0 ]]; then
    for serial in "${sim_devices[@]}"; do
        install_apk "$SIM_APK" "$serial" "Probe Simulator" "com.esper.probesimulator"
    done
else
    warn "Probe Simulator: no devices selected, skipped"
fi

echo ""
success "Done."
