#!/usr/bin/env bash
#
# 12306 微服务顺序启动脚本 (Windows / Git Bash)
#
# 启动顺序（依赖关系决定）：
#   demo(8080) -> agent(9090) -> monitor-agent(9091) -> gateway(10010)
#   gateway 是统一入口，路由到前面三个服务，必须最后启动。
#
# 用法：
#   ./start-all.sh                 顺序启动全部服务（默认会先构建）
#   ./start-all.sh start demo agent  只启动指定服务（仍按顺序）
#   ./start-all.sh stop [服务名...]  停止服务（默认全部）
#   ./start-all.sh restart         重启全部
#   ./start-all.sh status          查看各服务状态
#   ./start-all.sh build           只构建不启动
#   ./start-all.sh logs [服务名]    跟踪日志
#
# 选项：
#   --skip-build   跳过 maven 构建，直接启动已有 jar
#   --no-wait      不等待端口就绪，启动后立即返回
#   --timeout=120  单服务启动等待秒数，默认 120
#

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT/logs"
PID_DIR="$ROOT/logs/pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

MVN="${MVN:-mvn}"
JAVA="${JAVA:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"

# 服务定义：名称|端口|说明（数组顺序即启动顺序）
SERVICE_DEFS=(
  "demo|8080|主业务服务 (MySQL/Redis/RabbitMQ)"
  "agent|9090|智能客服 Agent (LangChain4j)"
  "monitor-agent|9091|多 Agent 监控分析服务"
  "gateway|10010|Spring Cloud Gateway 统一入口"
)

# 基础设施依赖：端口|名称
INFRA_DEFS=(
  "3306|MySQL"
  "6379|Redis"
  "5672|RabbitMQ"
)

# ---------- 参数 ----------
SKIP_BUILD=0
NO_WAIT=0
WAIT_TIMEOUT=120

ARGS=()
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --no-wait)    NO_WAIT=1 ;;
    --timeout=*)  WAIT_TIMEOUT="${arg#*=}" ;;
    -h|--help)    sed -n '3,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)            ARGS+=("$arg") ;;
  esac
done

CMD="${ARGS[0]:-start}"
TARGETS=("${ARGS[@]:1}")

# ---------- 输出 ----------
if [ -t 1 ]; then
  C_RESET=$'\033[0m'; C_RED=$'\033[31m'; C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'; C_BLUE=$'\033[36m'; C_DIM=$'\033[2m'
else
  C_RESET=""; C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_DIM=""
fi

info()  { echo "${C_BLUE}[信息]${C_RESET} $*"; }
ok()    { echo "${C_GREEN}[成功]${C_RESET} $*"; }
warn()  { echo "${C_YELLOW}[警告]${C_RESET} $*"; }
fail()  { echo "${C_RED}[失败]${C_RESET} $*"; }
step()  { echo; echo "${C_BLUE}==== $* ====${C_RESET}"; }

# ---------- 工具函数 ----------
# Git Bash 下把 POSIX 路径转成 Windows 路径，供 java 等原生程序使用
win_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

# 端口是否已被监听
port_open() {
  local port="$1"
  netstat -ano -p tcp 2>/dev/null \
    | grep -E "[:.]${port}[[:space:]]+[^[:space:]]+[[:space:]]+LISTENING" >/dev/null 2>&1
}

# 监听指定端口的进程 PID
pid_on_port() {
  local port="$1"
  netstat -ano -p tcp 2>/dev/null \
    | grep -E "[:.]${port}[[:space:]]+[^[:space:]]+[[:space:]]+LISTENING" \
    | awk '{print $NF}' | head -n 1
}

# 等待端口就绪；$1=端口 $2=服务名
wait_for_port() {
  local port="$1" name="$2" waited=0
  printf '%s' "    等待 ${name} 端口 ${port} 就绪"
  while [ "$waited" -lt "$WAIT_TIMEOUT" ]; do
    if port_open "$port"; then
      echo; ok "${name} 已就绪 (端口 ${port})"
      return 0
    fi
    # 进程已退出就没必要继续等了
    if ! kill -0 "$(cat "$PID_DIR/$name.pid" 2>/dev/null)" 2>/dev/null; then
      echo; fail "${name} 进程已退出，请查看日志: logs/${name}.log"
      return 1
    fi
    printf '.'
    sleep 2
    waited=$((waited + 2))
  done
  echo; fail "${name} 在 ${WAIT_TIMEOUT}s 内未就绪，请查看日志: logs/${name}.log"
  return 1
}

# 找到服务对应的可执行 jar
find_jar() {
  local dir="$1" jar
  jar="$(find "$dir/target" -maxdepth 1 -type f -name '*.jar' \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*.original' 2>/dev/null | head -n 1)"
  printf '%s' "$jar"
}

# 判断服务名是否在本次操作范围内
in_targets() {
  [ "${#TARGETS[@]}" -eq 0 ] && return 0
  local t
  for t in "${TARGETS[@]}"; do [ "$t" = "$1" ] && return 0; done
  return 1
}

# 校验服务名
validate_targets() {
  local t found
  for t in "${TARGETS[@]}"; do
    found=0
    for def in "${SERVICE_DEFS[@]}"; do
      [ "${def%%|*}" = "$t" ] && found=1
    done
    if [ "$found" -eq 0 ]; then
      fail "未知服务: $t"
      echo "可用服务: $(printf '%s ' "${SERVICE_DEFS[@]%%|*}")"
      exit 1
    fi
  done
}

# ---------- 基础设施检查 ----------
check_infra() {
  local missing=()
  local def port name
  for def in "${INFRA_DEFS[@]}"; do
    port="${def%%|*}"; name="${def##*|}"
    port_open "$port" || missing+=("${name}(${port})")
  done
  if [ "${#missing[@]}" -gt 0 ]; then
    warn "以下基础设施未启动，相关服务可能启动失败：${missing[*]}"
    return 1
  fi
  return 0
}

# ---------- 构建 ----------
build_service() {
  local name="$1"
  info "构建 ${name} ..."
  local mvn_cmd="$MVN"
  command -v "$mvn_cmd" >/dev/null 2>&1 || { fail "找不到 maven 命令: $mvn_cmd（可用 MVN=路径 指定）"; return 1; }
  # maven.test.skip 连测试代码一起跳过：测试源码编译错误不应阻塞服务启动
  if ! ( cd "$ROOT/$name" && "$mvn_cmd" -q -Dmaven.test.skip=true package ); then
    fail "${name} 构建失败"
    return 1
  fi
  ok "${name} 构建完成"
  return 0
}

# ---------- 启动 ----------
start_service() {
  local name="$1" port="$2" desc="$3"

  step "启动 ${name} (${desc})"

  if port_open "$port"; then
    warn "${name} 已在运行 (端口 ${port} 被占用)，跳过"
    return 0
  fi

  if [ "$SKIP_BUILD" -eq 0 ]; then
    build_service "$name" || return 1
  fi

  local jar
  jar="$(find_jar "$ROOT/$name")"
  if [ -z "$jar" ]; then
    fail "未找到 ${name} 的可执行 jar，请先执行: ./start-all.sh build"
    return 1
  fi

  local log_file="$LOG_DIR/${name}.log"
  local pid_file="$PID_DIR/${name}.pid"

  info "启动: java -jar $(basename "$jar")"
  info "日志: logs/${name}.log"

  # shellcheck disable=SC2086
  nohup "$JAVA" $JAVA_OPTS -jar "$(win_path "$jar")" \
    > "$(win_path "$log_file")" 2>&1 &
  local pid=$!
  echo "$pid" > "$pid_file"
  disown 2>/dev/null || true

  if [ "$NO_WAIT" -eq 1 ]; then
    ok "${name} 已后台启动 (pid ${pid})"
    return 0
  fi

  wait_for_port "$port" "$name" || return 1
}

# ---------- 停止 ----------
stop_service() {
  local name="$1" port="$2"
  local pid_file="$PID_DIR/${name}.pid"
  local stopped=0

  if [ -f "$pid_file" ]; then
    local pid
    pid="$(cat "$pid_file")"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null && stopped=1
    fi
    rm -f "$pid_file"
  fi

  # 兜底：按端口占用进程强杀
  if port_open "$port"; then
    local wpid
    wpid="$(pid_on_port "$port")"
    if [ -n "$wpid" ]; then
      taskkill //F //PID "$wpid" >/dev/null 2>&1 && stopped=1
    fi
  fi

  local waited=0
  while [ "$waited" -lt 20 ] && port_open "$port"; do sleep 1; waited=$((waited + 1)); done

  if port_open "$port"; then
    fail "${name} 停止失败 (端口 ${port} 仍被占用)"
    return 1
  fi
  if [ "$stopped" -eq 1 ]; then ok "${name} 已停止"; else info "${name} 未在运行"; fi
  return 0
}

# ---------- 状态 ----------
show_status() {
  # 中文为全角，printf 的 %-Ns 按字符数补位会错位，故状态列手工补空格
  # 列宽: 服务名 16 列 | 端口 8 列 | 状态 12 列（"运行中"/"未运行" 各占 6 列）
  printf '%s\n' "服务            端口    状态        说明"
  printf '%s\n' "--------------  ------  ----------  ----"
  local def name port desc
  for def in "${SERVICE_DEFS[@]}"; do
    name="${def%%|*}"; port="${def#*|}"; port="${port%%|*}"; desc="${def##*|}"
    if port_open "$port"; then
      printf '%-16s%-8s%s运行中%s      %s\n' "$name" "$port" "${C_GREEN}" "${C_RESET}" "$desc"
    else
      printf '%-16s%-8s%s未运行%s      %s\n' "$name" "$port" "${C_DIM}" "${C_RESET}" "$desc"
    fi
  done
}

# ---------- 主流程 ----------
run_start() {
  echo "${C_BLUE}12306 微服务启动脚本${C_RESET}  工作目录: $ROOT"
  check_infra || warn "继续启动，请自行确认基础设施状态"

  local failed=()
  local def name port desc
  for def in "${SERVICE_DEFS[@]}"; do
    name="${def%%|*}"; port="${def#*|}"; port="${port%%|*}"; desc="${def##*|}"
    in_targets "$name" || continue
    start_service "$name" "$port" "$desc" || failed+=("$name")
  done

  echo
  show_status

  if [ "${#failed[@]}" -gt 0 ]; then
    echo
    fail "启动失败的服务: ${failed[*]}"
    echo "排查日志: tail -f logs/<服务名>.log"
    exit 1
  fi

  echo
  if [ "${#TARGETS[@]}" -eq 0 ]; then
    ok "全部完成。访问入口: http://localhost:10010"
  else
    ok "完成。"
  fi
}

run_stop() {
  local def name port
  for def in "${SERVICE_DEFS[@]}"; do
    name="${def%%|*}"; port="${def#*|}"; port="${port%%|*}"
    in_targets "$name" || continue
    stop_service "$name" "$port"
  done
}

run_build() {
  local def name
  for def in "${SERVICE_DEFS[@]}"; do
    name="${def%%|*}"
    in_targets "$name" || continue
    build_service "$name" || exit 1
  done
  ok "构建完成"
}

run_logs() {
  local target="${TARGETS[0]:-gateway}"
  local log_file="$LOG_DIR/${target}.log"
  [ -f "$log_file" ] || { fail "日志文件不存在: logs/${target}.log"; exit 1; }
  tail -n 50 -f "$log_file"
}

validate_targets

case "$CMD" in
  start)   run_start ;;
  stop)    run_stop ;;
  restart) run_stop; run_start ;;
  status)  show_status ;;
  build)   run_build ;;
  logs)    run_logs ;;
  *)
    fail "未知命令: $CMD"
    echo "用法: $0 {start|stop|restart|status|build|logs} [服务名...] [--skip-build] [--no-wait] [--timeout=120]"
    exit 1
    ;;
esac
