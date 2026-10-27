#!/usr/bin/env bash
# Builds, tests, packages and runs every component.
#
# Build
#   scripts/build.sh                  all components, with tests
#   scripts/build.sh backend daemon   only these (backend | daemon | web)
#   scripts/build.sh --skip-tests     package without running tests
#   scripts/build.sh test             run every test suite, build nothing
#   scripts/build.sh clean            remove dist/ and build outputs
#
# Official demo data
#   scripts/build.sh demo             generate demo/: a random password per demo account (CREDENTIALS.md,
#                                     credentials.env, both 0600 and gitignored) and seed.sql (telemetry, inbox)
#   scripts/build.sh demo --force     rotate every demo password (reload the data afterwards)
#
# Run (foreground; Ctrl+C stops everything, the daemon first so it removes its blocks)
#   scripts/build.sh run              dev: embedded Postgres + Redis with demo data (no Docker),
#                                     API :8080, dashboard :3000 (next dev), daemon in --dry-run
#   scripts/build.sh run --prod       Docker Postgres + Redis, dist/obsidian-backend.jar, next start
#                                     (first brings the jar, daemon and dashboard up to date)
#     --no-daemon                     don't start the firewall daemon
#     --daemon-root                   real enforcement: run the daemon with sudo (iptables, /etc/hosts)
#     --agent-token FILE              use this agent token instead of having the backend register one
#     --demo                          load the official demo data from demo/ (run `scripts/build.sh demo` first)
#                                     and have the backend apply demo/rules.json through RuleService
#   Logs: .run/<module>.log (also streamed to the terminal with a [module] prefix).
#   From another terminal: kill "$(cat .run/run.pid)" stops a run the same way as Ctrl+C.
#
# Outputs:
#   dist/obsidian-backend.jar   java -jar dist/obsidian-backend.jar   (reads .env or env vars)
#   dist/daemon-rust            sudo dist/daemon-rust --token-file /etc/obsidian/agent.token
#   web-dashboard/.next         cd web-dashboard && npm start          (production server on :3000)
#
# Environment:
#   JAVA_HOME             JDK 21–25 to use; otherwise one is looked for (JDK 26+ breaks Byte Buddy in tests)
#   GRADLE                gradle command to use; otherwise ./gradlew, or a system gradle 9 if the wrapper
#                         distribution can't be downloaded
#   NEXT_PUBLIC_API_MODE  baked into the web build; defaults to "live"
#   OBSIDIAN_ADMIN_EMAIL  admin whose organization `run` registers the local daemon in
#                         (default: the dev seed's admin; with --demo, the official demo admin)
#   NEXT_PUBLIC_TURNSTILE_SITE_KEY  dashboard's Turnstile site key; defaults to TURNSTILE_SITE_KEY in .env
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIST="$ROOT/dist"
RUN="$ROOT/.run"
SKIP_TESTS=0
TARGETS=()
PROD=0
WITH_DAEMON=1
DAEMON_ROOT=0
AGENT_TOKEN=""
USE_DEMO=0
FORCE=0
DEMO_DIR="$ROOT/demo"

while [ $# -gt 0 ]; do
  case "$1" in
    --skip-tests) SKIP_TESTS=1 ;;
    --prod) PROD=1 ;;
    --no-daemon) WITH_DAEMON=0 ;;
    --daemon-root) DAEMON_ROOT=1 ;;
    --demo) USE_DEMO=1 ;;
    --force) FORCE=1 ;;
    --agent-token) [ $# -ge 2 ] || { echo "--agent-token needs a file" >&2; exit 2; }; AGENT_TOKEN=$(realpath "$2"); shift ;;
    backend | daemon | web | test | clean | run | demo) TARGETS+=("$1") ;;
    -h | --help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    *) echo "unknown argument: $1 (try --help)" >&2; exit 2 ;;
  esac
  shift
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

# The Turnstile site key the dashboard needs: $NEXT_PUBLIC_TURNSTILE_SITE_KEY, else TURNSTILE_SITE_KEY
# from the root .env (only that one line is read, the file isn't sourced).
turnstile_site_key() {
  if [ -n "${NEXT_PUBLIC_TURNSTILE_SITE_KEY:-}" ]; then
    printf '%s' "$NEXT_PUBLIC_TURNSTILE_SITE_KEY"
  elif [ -f "$ROOT/.env" ]; then
    sed -n 's/^TURNSTILE_SITE_KEY=//p' "$ROOT/.env" | tail -1 | tr -d '\r"'"'"
  fi
}

# What a web build bakes in; a --prod run rebuilds when it changed.
web_build_settings() {
  printf 'api=%s origin=%s turnstile=%s\n' "${NEXT_PUBLIC_API_MODE:-live}" "${SPRING_ORIGIN:-http://localhost:8080}" \
    "$(turnstile_site_key)"
}

# True when web-dashboard/.next is missing, older than its sources, or built with other settings.
web_build_stale() {
  local web="$ROOT/web-dashboard" built="$ROOT/web-dashboard/.next/BUILD_ID"
  [ -f "$built" ] || return 0
  [ "$(cat "$web/.next/obsidian-build-settings" 2>/dev/null)" = "$(web_build_settings)" ] || return 0
  [ -n "$(find "$web/src" "$web/public" "$web/next.config.ts" "$web/package-lock.json" -newer "$built" -print -quit 2>/dev/null)" ]
}

build_web() {
  need npm "install Node.js 20 or newer"
  [ "$SKIP_TESTS" = 1 ] || web_test
  web_install
  local site_key; site_key=$(turnstile_site_key)
  step "web: next build (NEXT_PUBLIC_API_MODE=${NEXT_PUBLIC_API_MODE:-live}, Turnstile ${site_key:+on}${site_key:-off})"
  (cd "$ROOT/web-dashboard" && NEXT_PUBLIC_API_MODE="${NEXT_PUBLIC_API_MODE:-live}" \
      NEXT_PUBLIC_TURNSTILE_SITE_KEY="$site_key" npm run --silent build)
  web_build_settings > "$ROOT/web-dashboard/.next/obsidian-build-settings"
}

clean() {
  step "clean"
  rm -rf "$DIST" "$ROOT/web-dashboard/.next"
  (cd "$ROOT/daemon-rust" && cargo clean --quiet) 2>/dev/null || true
  pick_java; pick_gradle
  (cd "$ROOT/backend-spring" && $GRADLE clean --console=plain -q) || true
}

# ---- run ------------------------------------------------------------------------

declare -A PIDS=()
ORDER=()
STARTED_COMPOSE=0

# start NAME DIR COMMAND... : background process with its own log, tracked for shutdown.
start() {
  local name=$1 dir=$2; shift 2
  (cd "$dir" && exec "$@") > "$RUN/$name.log" 2>&1 < /dev/null &
  PIDS[$name]=$!
  ORDER+=("$name")
}

kill_tree() {
  local pid=$1 signal=${2:-TERM} child
  for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child" "$signal"; done
  kill "-$signal" "$pid" 2>/dev/null || true
}

stop_one() {
  local name=$1 pid=${PIDS[$1]:-}
  [ -n "$pid" ] || return 0
  if [ "$name" = daemon ] && [ "$DAEMON_ROOT" = 1 ]; then
    sudo pkill -TERM -f "$ROOT/daemon-rust/target/release/daemon-rust" 2>/dev/null || true
  else
    kill_tree "$pid" TERM
  fi
  for _ in $(seq 1 50); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  kill -0 "$pid" 2>/dev/null && kill_tree "$pid" KILL
  unset "PIDS[$name]"
}

port_owner_is_ours() { # PORT PATTERN: our process still listening (Gradle can detach the app JVM)
  local pid
  pid=$(ss -ltnpH "sport = :$1" 2>/dev/null | grep -oE 'pid=[0-9]+' | head -1 | cut -d= -f2)
  [ -n "$pid" ] && ps -o args= -p "$pid" 2>/dev/null | grep -q -- "$2" && echo "$pid"
}

stop_all() {
  trap - EXIT INT TERM
  [ -n "${TAIL_PID:-}" ] && kill "$TAIL_PID" 2>/dev/null || true
  step "stopping"
  local i
  for (( i = ${#ORDER[@]} - 1; i >= 0; i-- )); do stop_one "${ORDER[$i]}"; done
  local leftover
  leftover=$(port_owner_is_ours 8080 'com.obsidian' || true); [ -n "$leftover" ] && kill "$leftover" 2>/dev/null || true
  leftover=$(port_owner_is_ours 3000 'next' || true); [ -n "$leftover" ] && kill "$leftover" 2>/dev/null || true
  if [ "$STARTED_COMPOSE" = 1 ]; then (cd "$ROOT" && docker compose stop >/dev/null 2>&1) || true; fi
  rm -f "$RUN/run.pid"
  echo "stopped; logs are in .run/"
}

on_signal() {
  stop_all
  exit 130
}

# wait_for NAME URL SECONDS: until the URL answers, or fail if the process died.
wait_for() {
  local name=$1 url=$2 limit=$3 code
  for _ in $(seq 1 "$limit"); do
    code=$(curl -s -o /dev/null -m 2 -w '%{http_code}' "$url" || true)
    [ "$code" != 000 ] && return 0
    if ! kill -0 "${PIDS[$name]}" 2>/dev/null; then
      tail -n 30 "$RUN/$name.log" >&2
      fail "$name exited during startup (log: .run/$name.log)"
    fi
    sleep 1
  done
  tail -n 30 "$RUN/$name.log" >&2
  fail "$name didn't answer on $url within ${limit}s (log: .run/$name.log)"
}

port_free() {
  ! ss -ltnH "sport = :$1" 2>/dev/null | grep -q . || fail "port $1 is already in use; stop whatever runs there first"
}

# token_accepted FILE: does the running backend know this agent token? A /ws/firewall handshake
# answers 101 for a registered agent and 401 otherwise. The header goes through a 0600 file so the
# token never shows up in `ps`.
token_accepted() {
  local header code
  header=$(mktemp -p "$RUN" header.XXXXXX)
  chmod 600 "$header"
  printf 'Authorization: Bearer %s\n' "$(tr -d '[:space:]' < "$1")" > "$header"
  code=$(curl -s -o /dev/null -m 2 -w '%{http_code}' -H @"$header" -H 'Connection: Upgrade' \
      -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: b2JzaWRpYW4tY2hlY2stMQ==' \
      http://localhost:8080/ws/firewall || true)
  rm -f "$header"
  [ "$code" = 101 ]
}

# ---- official demo data -------------------------------------------------------------

demo_generate() {
  pick_java; pick_gradle
  step "official demo data → demo/"
  if [ -f "$DEMO_DIR/credentials.env" ] && [ "$FORCE" = 0 ]; then
    echo "demo/credentials.env already exists; keeping it (rotate with: scripts/build.sh demo --force)"
    return
  fi
  local args="$DEMO_DIR"
  [ "$FORCE" = 1 ] && args="$args --force"
  (cd "$ROOT/backend-spring" && $GRADLE generateDemoData --console=plain -q --args="$args")
  echo "credentials: demo/CREDENTIALS.md (never commit or share it)"
}

load_demo_credentials() {
  [ -f "$DEMO_DIR/credentials.env" ] && [ -f "$DEMO_DIR/seed.sql" ] && [ -f "$DEMO_DIR/rules.json" ] \
    || fail "no official demo data yet: run scripts/build.sh demo first"
  # shellcheck disable=SC1091 # generated: KEY=value lines, values are [a-z0-9@.-] only
  . "$DEMO_DIR/credentials.env"
  export OBSIDIAN_ADMIN_EMAIL=$DEMO_ADMIN_EMAIL
}

run_all() {
  need curl "used to check the services"
  need npm "install Node.js 20 or newer"
  mkdir -p "$RUN"
  rm -f "$RUN"/*.log
  port_free 8080
  port_free 3000
  trap stop_all EXIT
  trap on_signal INT TERM
  echo $$ > "$RUN/run.pid"   # kill "$(cat .run/run.pid)" stops a run from another terminal
  pick_java; pick_gradle
  local seed="$ROOT/scripts/seed.sql"
  if [ "$USE_DEMO" = 1 ]; then
    load_demo_credentials
    seed="$DEMO_DIR/seed.sql"
  fi
  # Demo rules and the daemon's agent token are set up by the backend itself at startup
  # (StartupBootstrap): a script can't sign in once Turnstile is on.
  local boot_args=() token_file=${AGENT_TOKEN:-$RUN/agent.token}
  [ "$USE_DEMO" = 1 ] && boot_args+=("--obsidian.bootstrap.rules=$DEMO_DIR/rules.json")
  if [ "$WITH_DAEMON" = 1 ] && [ -z "$AGENT_TOKEN" ]; then
    local host; host=$(hostname | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9.\n-' '-')
    boot_args+=("--obsidian.bootstrap.agent-token-file=$token_file"
                "--obsidian.bootstrap.agent-admin=${OBSIDIAN_ADMIN_EMAIL:-avery.lin@aster.example}"
                "--obsidian.bootstrap.agent-hostname=${host:-localhost}")
  fi
  local site_key; site_key=$(turnstile_site_key)

  if [ "$PROD" = 1 ]; then
    need docker "option --prod runs Postgres and Redis in Docker"
    if ! (cd "$ROOT" && docker compose ps --status running --services 2>/dev/null | grep -q postgres); then
      STARTED_COMPOSE=1
    fi
    step "postgres + redis (docker compose)"
    (cd "$ROOT" && docker compose up -d --wait)
    (cd "$ROOT" && docker compose exec -T postgres psql -q -v ON_ERROR_STOP=1 \
        -U "${POSTGRES_USER:-obsidian}" -d "${POSTGRES_DB:-obsidian}" < "$seed") >/dev/null
    # Always bring the jar up to date (Gradle skips the work when nothing changed): an old jar silently
    # lacks newer behaviour, e.g. the startup setup that writes the agent token.
    SKIP_TESTS=1 build_backend
    if web_build_stale; then
      SKIP_TESTS=1 build_web
    else
      echo "dashboard build is up to date"
    fi
    step "starting backend (dist/obsidian-backend.jar)"
    start backend "$ROOT" "$JAVA_HOME/bin/java" -jar "$DIST/obsidian-backend.jar" "${boot_args[@]}"
  else
    step "starting backend (gradle bootTestRun: embedded Postgres + Redis, seed ${seed#"$ROOT"/})"
    start backend "$ROOT/backend-spring" env OBSIDIAN_SEED_FILE="$seed" \
        $GRADLE bootTestRun --console=plain -q --args="${boot_args[*]}"
  fi
  wait_for backend http://localhost:8080/api/v1/auth/me 240
  if [ "$WITH_DAEMON" = 1 ] && [ -z "$AGENT_TOKEN" ]; then
    # The startup setup runs just after the server opens its port; wait until it has written a token
    # this database accepts (demo rules are applied before that).
    local ok=0
    for _ in $(seq 1 30); do
      [ -s "$token_file" ] && token_accepted "$token_file" && { ok=1; break; }
      kill -0 "${PIDS[backend]}" 2>/dev/null || break
      sleep 1
    done
    [ "$ok" = 1 ] || { tail -n 30 "$RUN/backend.log" >&2; fail "the backend didn't set up the agent token (log: .run/backend.log)"; }
    [ "$USE_DEMO" = 0 ] || echo "demo rules applied (demo/rules.json); testsafebrowsing.appspot.com is left for the live demo"
  fi

  web_install
  if [ "$PROD" = 1 ]; then
    step "starting dashboard (next start)"
    start web "$ROOT/web-dashboard" npm start
  else
    step "starting dashboard (next dev, live API)"
    start web "$ROOT/web-dashboard" env NEXT_PUBLIC_API_MODE=live NEXT_PUBLIC_TURNSTILE_SITE_KEY="$site_key" npm run dev
  fi
  wait_for web http://localhost:3000/login 120

  if [ "$WITH_DAEMON" = 1 ]; then
    need cargo "install a Rust stable toolchain, or pass --no-daemon"
    (cd "$ROOT/daemon-rust" && cargo build --release --quiet)   # incremental: quick when nothing changed
    if [ -n "$AGENT_TOKEN" ]; then
      token_accepted "$token_file" \
        || fail "the backend doesn't know the agent token in $AGENT_TOKEN (401); register the agent again on Endpoint Security"
    fi
    if [ "$DAEMON_ROOT" = 1 ]; then
      step "starting daemon (sudo: real iptables + /etc/hosts)"
      sudo -v || fail "sudo is needed for --daemon-root"
      start daemon "$ROOT" sudo "$ROOT/daemon-rust/target/release/daemon-rust" --token-file "$token_file"
    else
      step "starting daemon (--dry-run: prints iptables commands, hosts file .run/hosts)"
      [ -f "$RUN/hosts" ] || printf '127.0.0.1 localhost\n' > "$RUN/hosts"
      start daemon "$ROOT" "$ROOT/daemon-rust/target/release/daemon-rust" --dry-run \
          --token-file "$token_file" --hosts-file "$RUN/hosts"
    fi
    sleep 2
    kill -0 "${PIDS[daemon]}" 2>/dev/null || { tail -n 20 "$RUN/daemon.log" >&2; fail "daemon exited (log: .run/daemon.log)"; }
  fi

  if [ "$USE_DEMO" = 1 ]; then
    step "running the official demo — open http://localhost:3000 and sign in as $DEMO_ADMIN_EMAIL (passwords: demo/CREDENTIALS.md). Ctrl+C stops everything."
  else
    step "running — open http://localhost:3000 (sign in as avery.lin@aster.example / obsidian-demo-2026). Ctrl+C stops everything."
  fi
  echo "the Android app isn't built yet; its prototype is docs/prototype/android.html"
  tail -n +1 -F "$RUN"/*.log 2>/dev/null | awk '
      /^==> .* <==$/ { name = $2; sub(/.*\//, "", name); sub(/\.log$/, "", name); next }
      /^tail: / || /^[[:space:]]*$/ { next }
      { printf "[%s] %s\n", name, $0; fflush() }' &
  TAIL_PID=$!
  # Exit (and clean up) as soon as any module stops on its own.
  while :; do
    for name in "${ORDER[@]}"; do
      if ! kill -0 "${PIDS[$name]}" 2>/dev/null; then
        echo "$name stopped on its own (log: .run/$name.log)" >&2
        exit 1
      fi
    done
    sleep 2
  done
}

started=$(date +%s)
if [[ " ${TARGETS[*]} " == *" run "* ]]; then
  [ ${#TARGETS[@]} -eq 1 ] || fail "run can't be combined with other targets"
  run_all
fi
for target in "${TARGETS[@]}"; do
  case "$target" in
    backend) build_backend ;;
    daemon) build_daemon ;;
    web) build_web ;;
    test) pick_java; pick_gradle; backend_test; daemon_test; web_test ;;
    demo) demo_generate ;;
    clean) clean ;;
  esac
done

step "done in $(( $(date +%s) - started ))s"
[ -d "$DIST" ] && ls -lh "$DIST" | tail -n +2 || true
