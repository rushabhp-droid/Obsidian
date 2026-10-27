#!/usr/bin/env bash
# Builds, tests and packages every component into dist/.
#
#   scripts/build.sh                  all components, with tests
#   scripts/build.sh backend daemon   only these (backend | daemon | web)
#   scripts/build.sh --skip-tests     package without running tests
#   scripts/build.sh test             run every test suite, build nothing
#   scripts/build.sh clean            remove dist/ and build outputs
#
# Outputs:
#   dist/obsidian-backend.jar   java -jar dist/obsidian-backend.jar   (reads ../.env or env vars)
#   dist/daemon-rust            sudo dist/daemon-rust --token-file /etc/obsidian/agent.token
#   web-dashboard/.next         cd web-dashboard && npm start          (production server on :3000)
#
# Environment:
#   JAVA_HOME             JDK 21–25 to use; otherwise one is looked for (JDK 26+ breaks Byte Buddy in tests)
#   GRADLE                gradle command to use; otherwise ./gradlew, or a system gradle 9 if the wrapper
#                         distribution can't be downloaded
#   NEXT_PUBLIC_API_MODE  baked into the web build; defaults to "live"
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIST="$ROOT/dist"
SKIP_TESTS=0
TARGETS=()

for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=1 ;;
    backend | daemon | web | test | clean) TARGETS+=("$arg") ;;
    -h | --help) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $arg (try --help)" >&2; exit 2 ;;
  esac
done
[ ${#TARGETS[@]} -gt 0 ] || TARGETS=(backend daemon web)

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "$1 is not installed ($2)"; }

java_major() { "$1/bin/java" -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1; }

pick_java() {
  if [ -n "${JAVA_HOME:-}" ]; then
    local major; major=$(java_major "$JAVA_HOME")
    [ -n "$major" ] || fail "JAVA_HOME=$JAVA_HOME has no bin/java"
    [ "$major" -ge 21 ] || fail "JAVA_HOME is JDK $major; JDK 21 or newer is required"
    [ "$major" -le 25 ] || echo "warning: JDK $major is newer than the test tooling supports; use JDK 21–25 if tests fail" >&2
    return
  fi
  local candidate major
  local system_java; system_java=$(command -v java 2>/dev/null || true)
  for candidate in \
      "${system_java:+$(dirname "$(dirname "$(readlink -f "$system_java")")")}" \
      /usr/lib/jvm/* "$HOME"/.sdkman/candidates/java/* "$HOME"/.jdks/* /opt/android-studio/jbr; do
    [ -n "$candidate" ] && [ -x "$candidate/bin/java" ] || continue
    major=$(java_major "$candidate")
    if [ -n "$major" ] && [ "$major" -ge 21 ] && [ "$major" -le 25 ]; then
      export JAVA_HOME="$candidate"
      return
    fi
  done
  fail "no JDK 21–25 found; install one or set JAVA_HOME"
}

pick_gradle() {
  if [ -n "${GRADLE:-}" ]; then return; fi
  local dist="$HOME/.gradle/wrapper/dists"
  local version; version=$(sed -nE 's/.*gradle-([0-9.]+)-bin\.zip/\1/p' "$ROOT/backend-spring/gradle/wrapper/gradle-wrapper.properties")
  if compgen -G "$dist/gradle-$version-bin/*/gradle-$version/bin/gradle" >/dev/null; then
    GRADLE="./gradlew"                       # wrapper already downloaded
  elif command -v gradle >/dev/null 2>&1 && gradle --version 2>/dev/null | grep -q "^Gradle $version"; then
    GRADLE="gradle"                          # same version installed system-wide
  else
    GRADLE="./gradlew"                       # downloads Gradle $version on first run
  fi
}

backend_test() {
  step "backend: tests (embedded PostgreSQL 16 + in-process Redis)"
  (cd "$ROOT/backend-spring" && $GRADLE test --console=plain -q)
}

daemon_test() {
  step "daemon: tests"
  (cd "$ROOT/daemon-rust" && cargo test --quiet)
}

web_test() {
  step "web: install, typecheck, lint"
  web_install
  (cd "$ROOT/web-dashboard" && npx --no-install tsc --noEmit && npm run --silent lint)
}

web_install() {
  if [ ! -d "$ROOT/web-dashboard/node_modules" ] \
      || [ "$ROOT/web-dashboard/package-lock.json" -nt "$ROOT/web-dashboard/node_modules/.package-lock.json" ]; then
    (cd "$ROOT/web-dashboard" && npm ci --no-audit --no-fund)
  fi
}

build_backend() {
  pick_java; pick_gradle
  echo "using JDK $(java_major "$JAVA_HOME") at $JAVA_HOME, $GRADLE"
  [ "$SKIP_TESTS" = 1 ] || backend_test
  step "backend: bootJar"
  (cd "$ROOT/backend-spring" && $GRADLE bootJar --console=plain -q)
  mkdir -p "$DIST"
  cp "$(ls -t "$ROOT"/backend-spring/build/libs/*.jar | grep -v -- '-plain\.jar$' | head -1)" "$DIST/obsidian-backend.jar"
}

build_daemon() {
  need cargo "install a Rust stable toolchain"
  [ "$SKIP_TESTS" = 1 ] || daemon_test
  step "daemon: release build"
  (cd "$ROOT/daemon-rust" && cargo build --release --quiet)
  mkdir -p "$DIST"
  cp "$ROOT/daemon-rust/target/release/daemon-rust" "$DIST/daemon-rust"
}

build_web() {
  need npm "install Node.js 20 or newer"
  [ "$SKIP_TESTS" = 1 ] || web_test
  web_install
  step "web: next build (NEXT_PUBLIC_API_MODE=${NEXT_PUBLIC_API_MODE:-live})"
  (cd "$ROOT/web-dashboard" && NEXT_PUBLIC_API_MODE="${NEXT_PUBLIC_API_MODE:-live}" npm run --silent build)
}

clean() {
  step "clean"
  rm -rf "$DIST" "$ROOT/web-dashboard/.next"
  (cd "$ROOT/daemon-rust" && cargo clean --quiet) 2>/dev/null || true
  pick_java; pick_gradle
  (cd "$ROOT/backend-spring" && $GRADLE clean --console=plain -q) || true
}

started=$(date +%s)
for target in "${TARGETS[@]}"; do
  case "$target" in
    backend) build_backend ;;
    daemon) build_daemon ;;
    web) build_web ;;
    test) pick_java; pick_gradle; backend_test; daemon_test; web_test ;;
    clean) clean ;;
  esac
done

step "done in $(( $(date +%s) - started ))s"
[ -d "$DIST" ] && ls -lh "$DIST" | tail -n +2 || true
