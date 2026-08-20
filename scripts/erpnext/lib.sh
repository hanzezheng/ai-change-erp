#!/usr/bin/env bash
# 自动识别 frappe_docker 的 backend 容器（compose 项目名不同，容器名会变化）。

erp_list_running_containers() {
  docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
}

erp_detect_backend_container() {
  local name image

  if [[ -n "${ERP_CONTAINER:-}" ]]; then
    if docker ps --format '{{.Names}}' | grep -qx "$ERP_CONTAINER"; then
      echo "$ERP_CONTAINER"
      return 0
    fi
    echo "警告：指定的 ERP_CONTAINER=$ERP_CONTAINER 未运行，尝试自动识别…" >&2
  fi

  local -a candidates=()
  while read -r name image; do
    [[ -n "$name" ]] || continue
    [[ "$image" == *erpnext* || "$image" == *frappe* ]] || continue
    [[ "$name" == *backend* ]] || continue
    candidates+=("$name")
  done < <(docker ps --format '{{.Names}} {{.Image}}')

  for name in "${candidates[@]}"; do
    if docker exec "$name" bench --site "${ERP_SITE:-frontend}" list-apps >/dev/null 2>&1; then
      echo "$name"
      return 0
    fi
  done

  # 兜底：任意带 backend 的 running 容器，且能执行 bench
  while read -r name; do
    [[ "$name" == *backend* ]] || continue
    if docker exec "$name" bench --version >/dev/null 2>&1; then
      echo "$name"
      return 0
    fi
  done < <(docker ps --format '{{.Names}}')

  echo "错误：未找到可执行 bench 的 ERPNext backend 容器。" >&2
  echo "" >&2
  echo "当前运行的容器：" >&2
  erp_list_running_containers >&2
  echo "" >&2
  echo "请确认 frappe_docker 已启动：" >&2
  echo "  cd ~/frappe_docker && docker compose -f pwd.yml up -d" >&2
  echo "" >&2
  echo "若容器名不是 *-backend-1，可手动指定：" >&2
  echo "  ERP_CONTAINER=你的容器名 bash scripts/wsl-dev.sh" >&2
  return 1
}
