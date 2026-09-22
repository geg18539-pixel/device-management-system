#!/usr/bin/env bash
#
# 从 GHCR 拉取最新镜像并重启服务（Linux 服务器用）
#
# 用法：bash deploy/deploy.sh
#
# 和 Windows 那个 .bat 是同一件事。放在这里是因为服务器上
# 大概率没有 Windows 那套，而 .sh 可以配合 cron / 手动执行。
#
set -euo pipefail

cd "$(dirname "$0")/.."

OWNER=geg18539-pixel
REGISTRY=ghcr.io

echo
echo "============================================================"
echo "  设备管理系统 - 拉取最新镜像并重启"
echo "============================================================"
echo

echo "  [1/3] 拉取镜像..."
if ! docker compose pull backend frontend; then
    echo
    echo "  拉取失败，多半是还没登录 GHCR。下面登录一次再重试。"
    echo
    echo "    用户名：${OWNER}"
    echo "    密码：  GitHub 令牌（PAT），不是登录密码"
    echo "            令牌要勾上 read:packages 权限"
    echo
    docker login "${REGISTRY}"
    docker compose pull backend frontend
fi

echo
echo "  [2/3] 重建容器..."
docker compose up -d

echo
echo "  [3/3] 当前状态："
echo
docker compose ps

echo
echo "  完成。浏览器打开 http://localhost 即可访问。"
echo
