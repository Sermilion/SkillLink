#!/usr/bin/env bash
set -euo pipefail

REPO_OWNER="Sermilion"
REPO_NAME="SkillLink"
SKILLLINK_STATE_DIR="${HOME}/.skilllink"
INSTALL_DIR="${SKILLLINK_INSTALL_DIR:-$SKILLLINK_STATE_DIR/app}"
LAUNCHER_BIN_DIR="${SKILLLINK_BIN_DIR:-$HOME/.local/bin}"
CLONE_DIR=""
FROM_SOURCE=0
RELEASE_TAG="${SKILLLINK_RELEASE_TAG:-}"
INSTALL_SOURCE="auto"
MIN_JAVA_VERSION=21

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { printf "${CYAN}▸${NC} %s\n" "$1"; }
ok()    { printf "${GREEN}✓${NC} %s\n" "$1"; }
warn()  { printf "${YELLOW}⚠${NC} %s\n" "$1"; }
err()   { printf "${RED}✗${NC} %s\n" "$1" >&2; }

CLEANUP_DIR=""
cleanup() {
  if [[ -n "$CLEANUP_DIR" && -d "$CLEANUP_DIR" ]]; then
    rm -rf "$CLEANUP_DIR"
  fi
}
trap cleanup EXIT

usage() {
  cat <<'USAGE'
Usage: install.sh [OPTIONS]

Install the skill-link CLI for managing AI agent skills.

By default, the installer downloads a prebuilt CLI archive from the latest
GitHub release (no git, Gradle, or build-time JDK required). A JDK 21+ is
still needed at runtime. Use --from-source to build from the local checkout
instead (requires JDK 21 and a SkillLink repository).

Options:
  --from-source            Build from this local checkout with Gradle instead
                           of downloading a prebuilt archive. Requires JDK 21.
                           Equivalent to --local.
  --local                  Same as --from-source.
  --release TAG            Use a specific release tag. Downloads that tag's
                           prebuilt assets.
  --install-dir DIR        Directory for the CLI distribution
                           (default: ~/.skilllink/app).
  --bin-dir DIR            Directory for the skill-link launcher symlink
                           (default: ~/.local/bin).
  --skip-launcher          Install the distribution without creating launcher
                           symlinks.
  --help, -h               Show this help and exit.

Environment:
  JAVA_HOME                Path to a JDK 21+ installation (required at runtime
                           and for --from-source builds).
  SKILLLINK_INSTALL_DIR    Override --install-dir.
  SKILLLINK_BIN_DIR        Override --bin-dir.
  SKILLLINK_RELEASE_TAG    Override --release.

Examples:
  # Prebuilt install (recommended):
  curl -fsSL https://raw.githubusercontent.com/Sermilion/SkillLink/main/install.sh | bash

  # Specific release:
  curl -fsSL https://raw.githubusercontent.com/Sermilion/SkillLink/main/install.sh | bash -s -- --release v1.0.0

  # From source (local checkout):
  ./install.sh --from-source
USAGE
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --help|-h)
        usage
        exit 0
        ;;
      --from-source)
        FROM_SOURCE=1
        INSTALL_SOURCE="source"
        shift
        ;;
      --local)
        FROM_SOURCE=1
        INSTALL_SOURCE="source"
        shift
        ;;
      --release)
        if [[ $# -lt 2 || -z "$2" ]]; then
          err "--release requires a tag."
          exit 1
        fi
        RELEASE_TAG="$2"
        shift 2
        ;;
      --install-dir)
        if [[ $# -lt 2 || -z "$2" ]]; then
          err "--install-dir requires a path."
          exit 1
        fi
        INSTALL_DIR="$2"
        shift 2
        ;;
      --bin-dir)
        if [[ $# -lt 2 || -z "$2" ]]; then
          err "--bin-dir requires a path."
          exit 1
        fi
        LAUNCHER_BIN_DIR="$2"
        shift 2
        ;;
      --skip-launcher)
        SKIP_LAUNCHER=1
        shift
        ;;
      *)
        err "Unknown argument: $1"
        usage
        exit 1
        ;;
    esac
  done
}

SKIP_LAUNCHER=0

host_os() {
  local uname_s
  uname_s="$(uname -s 2>/dev/null || printf 'unknown')"
  case "$uname_s" in
    Darwin*)  printf 'macos' ;;
    Linux*)   printf 'linux' ;;
    MINGW*|MSYS*|CYGWIN*) printf 'windows' ;;
    *)        printf 'unknown' ;;
  esac
}

host_arch() {
  local uname_m
  uname_m="$(uname -m 2>/dev/null || printf 'unknown')"
  case "$uname_m" in
    arm64|aarch64) printf 'arm64' ;;
    x86_64|amd64)  printf 'x64' ;;
    *)             printf 'unknown' ;;
  esac
}

host_token() {
  local os arch
  os="$(host_os)"
  arch="$(host_arch)"
  printf '%s-%s' "$os" "$arch"
}

release_asset_token() {
  if [[ "$(host_os)" == "macos" ]]; then
    printf 'macos'
  else
    host_token
  fi
}

resolve_install_source() {
  if [[ "$INSTALL_SOURCE" != "auto" ]]; then
    return 0
  fi
  INSTALL_SOURCE="prebuilt"
}

check_prebuilt_dependencies() {
  local missing=()
  if ! command -v curl >/dev/null 2>&1; then
    missing+=("curl (to download release assets)")
  fi
  if ! command -v tar >/dev/null 2>&1; then
    missing+=("tar (to unpack the CLI archive)")
  fi
  if ! command -v shasum >/dev/null 2>&1 && ! command -v sha256sum >/dev/null 2>&1; then
    missing+=("shasum or sha256sum (to verify checksums)")
  fi
  if [[ ${#missing[@]} -gt 0 ]]; then
    err "Missing required tools for prebuilt install:"
    local item
    for item in "${missing[@]}"; do
      err "  - $item"
    done
    err "Install the tools above, or use --from-source to build instead."
    return 1
  fi
}

check_source_dependencies() {
  :
}

compute_sha256() {
  local file="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  else
    sha256sum "$file" | awk '{print $1}'
  fi
}

RESOLVED_LATEST_TAG=""

resolve_latest_release_tag() {
  if [[ -n "$RESOLVED_LATEST_TAG" ]]; then
    printf '%s' "$RESOLVED_LATEST_TAG"
    return 0
  fi
  local api_url json tags tag key best best_key
  api_url="https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=50"
  if ! json="$(curl -fsSL -H 'Accept: application/vnd.github+json' "$api_url")"; then
    err "Failed to query releases: $api_url"
    return 1
  fi
  tags="$(printf '%s' "$json" | grep -o '"tag_name"[[:space:]]*:[[:space:]]*"[^"]*"' \
    | sed -E 's/.*:[[:space:]]*"([^"]*)"/\1/')"
  best=""
  best_key=""
  while IFS= read -r tag; do
    [[ -n "$tag" ]] || continue
    key="$(printf '%s' "${tag#v}" \
      | awk -F. 'NF==3 && $1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ && $3 ~ /^[0-9]+$/ {
          printf "%010d.%010d.%010d", $1, $2, $3
        }')"
    [[ -n "$key" ]] || continue
    if [[ -z "$best_key" || "$key" > "$best_key" ]]; then
      best_key="$key"
      best="$tag"
    fi
  done <<< "$tags"
  if [[ -z "$best" ]]; then
    err "No releases found at: $api_url"
    return 1
  fi
  RESOLVED_LATEST_TAG="$best"
  printf '%s' "$best"
}

resolve_release_tag() {
  if [[ -n "$RELEASE_TAG" ]]; then
    printf '%s' "$RELEASE_TAG"
    return 0
  fi
  resolve_latest_release_tag
}

fetch_release_asset() {
  local name="$1"
  local dest="$2"
  local tag ref url
  tag="$(resolve_release_tag)" || return 1
  ref="download/$tag"
  url="https://github.com/$REPO_OWNER/$REPO_NAME/releases/$ref/$name"
  if ! curl -fsSL "$url" -o "$dest"; then
    err "Failed to download: $name"
    err "  from: $url"
    rm -f "$dest"
    return 1
  fi
}

verify_sha256() {
  local asset_path="$1"
  local checksum_path="$2"
  local expected actual
  expected="$(awk '{print $1}' "$checksum_path" | head -n1)"
  if [[ -z "$expected" ]]; then
    err "Empty or malformed checksum file."
    return 1
  fi
  actual="$(compute_sha256 "$asset_path")"
  if [[ "$actual" != "$expected" ]]; then
    err "Checksum mismatch:"
    err "  expected: $expected"
    err "  actual:   $actual"
    return 1
  fi
  ok "Checksum verified"
}

install_prebuilt() {
  check_prebuilt_dependencies || exit 1

  local token artifact_token tag asset_name
  token="$(host_token)"
  artifact_token="$(release_asset_token)"
  if ! tag="$(resolve_release_tag)"; then
    warn "No releases found. Falling back to --from-source build."
    INSTALL_SOURCE="source"
    FROM_SOURCE=1
    install_from_source
    return $?
  fi

  info "Installing prebuilt skill-link CLI ($tag) for $token"

  local supported_tokens="linux-x64 macos windows-x64"
  local found=0
  for t in $supported_tokens; do
    if [[ "$artifact_token" == "$t" ]]; then
      found=1
      break
    fi
  done

  if [[ "$found" -eq 0 ]]; then
    warn "No prebuilt archive for this platform ($token)."
    warn "Falling back to --from-source build."
    INSTALL_SOURCE="source"
    FROM_SOURCE=1
    install_from_source
    return $?
  fi

  asset_name="skill-link-cli-${artifact_token}.tar.gz"

  local tmpdir
  tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/skilllink-install.XXXXXX")"
  CLEANUP_DIR="$tmpdir"

  local archive_path="$tmpdir/$asset_name"
  local checksum_path="$tmpdir/${asset_name}.sha256"

  info "Downloading $asset_name..."
  if ! fetch_release_asset "$asset_name" "$archive_path"; then
    warn "Failed to download prebuilt archive. Falling back to --from-source build."
    INSTALL_SOURCE="source"
    FROM_SOURCE=1
    install_from_source
    return $?
  fi

  info "Downloading checksum..."
  if ! fetch_release_asset "${asset_name}.sha256" "$checksum_path"; then
    warn "Failed to download checksum. Falling back to --from-source build."
    INSTALL_SOURCE="source"
    FROM_SOURCE=1
    install_from_source
    return $?
  fi

  verify_sha256 "$archive_path" "$checksum_path" || exit 1

  local extract_dir="$tmpdir/extract"
  mkdir -p "$extract_dir"
  tar -xzf "$archive_path" -C "$extract_dir"

  if [[ ! -f "$extract_dir/bin/skill-link" ]]; then
    err "Archive does not contain bin/skill-link. Unexpected layout."
    exit 1
  fi

  install_from_dir "$extract_dir"
}

install_from_source() {
  check_source_dependencies
  resolve_java
  resolve_source_dir
  build_distribution

  local dist_dir="$CLONE_DIR/app/build/skill-link-cli"
  install_from_dir "$dist_dir"
}

install_from_dir() {
  local source_dir="$1"

  info "Installing to: $INSTALL_DIR"
  mkdir -p "$(dirname "$INSTALL_DIR")"

  if [[ -d "$INSTALL_DIR" ]]; then
    local backup="$INSTALL_DIR.prev"
    rm -rf "$backup"
    mv "$INSTALL_DIR" "$backup"
    info "Previous installation backed up to: $backup"
  fi

  cp -R "$source_dir" "$INSTALL_DIR"
  chmod +x "$INSTALL_DIR/bin/skill-link"
  if [[ -f "$INSTALL_DIR/bin/skill-link.bat" ]]; then
    chmod +x "$INSTALL_DIR/bin/skill-link.bat" 2>/dev/null || true
  fi

  ok "Distribution installed to: $INSTALL_DIR"
}

resolve_java() {
  local javacmd=""
  if [[ -n "${JAVA_HOME:-}" ]]; then
    javacmd="$JAVA_HOME/bin/java"
    if [[ ! -x "$javacmd" ]]; then
      err "JAVA_HOME is set but $javacmd is not executable."
      err "Set JAVA_HOME to a valid JDK $MIN_JAVA_VERSION+ installation."
      exit 1
    fi
  else
    javacmd="java"
    if ! command -v java >/dev/null 2>&1; then
      err "No java found on PATH and JAVA_HOME is not set."
      err "Install JDK $MIN_JAVA_VERSION+ and set JAVA_HOME or add it to PATH."
      exit 1
    fi
  fi

  local version_output
  version_output="$("$javacmd" -version 2>&1 | head -1)"
  local version_number
  version_number="$(printf '%s' "$version_output" | sed -E 's/.*"([0-9]+).*/\1/')"

  if [[ -z "$version_number" || "$version_number" -lt "$MIN_JAVA_VERSION" ]]; then
    err "JDK $MIN_JAVA_VERSION+ is required. Found: $version_output"
    err "Install JDK $MIN_JAVA_VERSION and set JAVA_HOME."
    exit 1
  fi

  ok "Found JDK $version_number ($javacmd)"
}

resolve_source_dir() {
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  if [[ ! -f "$script_dir/gradlew" ]]; then
    err "--from-source requires running from the SkillLink repository root."
    err "Could not find gradlew in: $script_dir"
    exit 1
  fi
  CLONE_DIR="$script_dir"
  ok "Using local checkout: $CLONE_DIR"
}

build_distribution() {
  info "Building skill-link CLI distribution..."
  local gradlew="$CLONE_DIR/gradlew"
  if [[ ! -x "$gradlew" ]]; then
    chmod +x "$gradlew"
  fi

  local build_output
  if ! build_output="$(cd "$CLONE_DIR" && ./gradlew :app:skillLinkCliDistribution --no-daemon 2>&1)"; then
    err "Gradle build failed:"
    printf '%s\n' "$build_output" >&2
    exit 1
  fi

  local dist_dir="$CLONE_DIR/app/build/skill-link-cli"
  if [[ ! -d "$dist_dir/lib" || ! -f "$dist_dir/bin/skill-link" ]]; then
    err "Build completed but distribution not found at: $dist_dir"
    exit 1
  fi

  ok "CLI distribution built"
}

install_launcher() {
  if [[ "$SKIP_LAUNCHER" -eq 1 ]]; then
    info "Skipping launcher installation (--skip-launcher)."
    return 0
  fi

  mkdir -p "$LAUNCHER_BIN_DIR"

  local launcher="$LAUNCHER_BIN_DIR/skill-link"
  local target="$INSTALL_DIR/bin/skill-link"

  if [[ -e "$launcher" && ! -L "$launcher" ]]; then
    warn "Skipping launcher: $launcher exists and is not a symlink."
    warn "Remove it manually or use --bin-dir to choose another directory."
    return 0
  fi

  ln -sfn "$target" "$launcher"
  ok "Launcher linked: $launcher -> $target"

  if [[ "$(host_os)" == "windows" ]]; then
    local bat_target="$INSTALL_DIR/bin/skill-link.bat"
    if [[ -f "$bat_target" ]]; then
      ln -sfn "$bat_target" "$LAUNCHER_BIN_DIR/skill-link.bat" 2>/dev/null || \
        cp "$bat_target" "$LAUNCHER_BIN_DIR/skill-link.bat" 2>/dev/null || true
    fi
  fi
}

path_contains_dir() {
  case ":${PATH:-}:" in
    *":$1:"*) return 0 ;;
    *) return 1 ;;
  esac
}

print_path_warning() {
  if path_contains_dir "$LAUNCHER_BIN_DIR"; then
    return 0
  fi
  local rc_file
  case "${SHELL:-}" in
    */fish) rc_file="${HOME}/.config/fish/config.fish" ;;
    */zsh)  rc_file="${HOME}/.zshrc" ;;
    *)      rc_file="${HOME}/.bashrc" ;;
  esac
  echo ""
  warn "Launcher directory is not on PATH: $LAUNCHER_BIN_DIR"
  case "${SHELL:-}" in
    */fish)
      warn "  Add it permanently:"
      warn "    fish_add_path $LAUNCHER_BIN_DIR"
      ;;
    *)
      warn "  Add it for the current session:"
      warn "    export PATH=\"$LAUNCHER_BIN_DIR:\$PATH\""
      warn "  Or add that line to $rc_file, then: source $rc_file"
      ;;
  esac
}

check_runtime_java() {
  local javacmd=""
  if [[ -n "${JAVA_HOME:-}" ]]; then
    javacmd="$JAVA_HOME/bin/java"
  elif command -v java >/dev/null 2>&1; then
    javacmd="java"
  fi

  if [[ -z "$javacmd" ]]; then
    echo ""
    warn "JDK $MIN_JAVA_VERSION+ is required at runtime to run skill-link."
    warn "Install JDK $MIN_JAVA_VERSION and ensure java is on PATH or set JAVA_HOME."
    return 0
  fi

  local version_output version_number
  version_output="$("$javacmd" -version 2>&1 | head -1)"
  version_number="$(printf '%s' "$version_output" | sed -E 's/.*"([0-9]+).*/\1/')"

  if [[ -z "$version_number" || "$version_number" -lt "$MIN_JAVA_VERSION" ]]; then
    echo ""
    warn "skill-link requires JDK $MIN_JAVA_VERSION+ at runtime. Found: $version_output"
    warn "Install JDK $MIN_JAVA_VERSION and ensure java is on PATH or set JAVA_HOME."
  else
    ok "Runtime JDK $version_number available"
  fi
}

print_summary() {
  echo ""
  printf "${GREEN}━━━ SkillLink installation complete ━━━${NC}\n"
  echo ""
  info "Distribution: $INSTALL_DIR"
  if [[ "$SKIP_LAUNCHER" -ne 1 ]]; then
    info "Launcher:     $LAUNCHER_BIN_DIR/skill-link"
  fi
  info "Data root:    $SKILLLINK_STATE_DIR"
  echo ""
  info "Verify the installation:"
  info "  skill-link --version"
  info "  skill-link --help"
  echo ""
  info "Install a skill into selected agents:"
  info "  skill-link install /path/to/my-skill/SKILL.md --agent claude --agent cursor"
  echo ""
  info "Optional agent helper skill (not installed automatically):"
  info "  skill-link install \"$INSTALL_DIR/share/skills/skill-link-operations/SKILL.md\" --agent claude --agent cursor"
  echo ""
  info "List managed skills:"
  info "  skill-link list"
  echo ""
}

run_install() {
  echo ""
  printf "${CYAN}━━━ SkillLink Installer ━━━${NC}\n"
  echo ""

  resolve_install_source

  if [[ "$INSTALL_SOURCE" == "prebuilt" ]]; then
    install_prebuilt
  else
    install_from_source
  fi

  install_launcher

  if [[ "$INSTALL_SOURCE" == "prebuilt" ]]; then
    check_runtime_java
  fi

  print_path_warning
  print_summary
}

parse_args "$@"
run_install
