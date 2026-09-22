@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0.."

echo.
echo  ============================================================
echo   设备管理系统 - 拉取最新镜像并重启
echo  ============================================================
echo.
echo  这个脚本做三件事：从 GHCR 拉最新镜像 -^> 重建容器 -^> 显示状态
echo.

echo  [1/3] 拉取镜像...
docker compose pull backend frontend
if not errorlevel 1 goto up

echo.
echo  拉取失败，多半是还没登录 GHCR。下面登录一次再重试。
echo.
echo    用户名：geg18539-pixel
echo    密码：  GitHub 令牌（PAT），不是你的登录密码
echo            令牌要勾上 read:packages 权限
echo            生成地址：https://github.com/settings/tokens
echo.
pause
docker login ghcr.io
if errorlevel 1 goto fail_login

echo.
echo  登录成功，重新拉取...
docker compose pull backend frontend
if errorlevel 1 goto fail_pull

:up
echo.
echo  [2/3] 重建容器...
docker compose up -d
if errorlevel 1 goto fail_up

echo.
echo  [3/3] 当前状态：
echo.
docker compose ps

echo.
echo  完成。浏览器打开 http://localhost 即可访问。
echo.
pause
exit /b 0


:fail_login
echo.
echo  [失败] 登录 GHCR 没有成功。
echo.
echo  检查两件事：
echo    1. 用户名是不是 geg18539-pixel（不是邮箱）
echo    2. 密码填的是令牌，不是 GitHub 登录密码
echo.
pause
exit /b 1


:fail_pull
echo.
echo  [失败] 登录成功了，但镜像还是拉不下来。
echo.
echo  最可能的原因是这个镜像还没被构建过。去这里看一眼：
echo    https://github.com/geg18539-pixel/device-management-system/actions
echo  看「构建并推送镜像」这个工作流有没有跑成功。第一次跑大概要几分钟。
echo.
pause
exit /b 1


:fail_up
echo.
echo  [失败] 容器启动失败，下面是最后 50 行日志：
echo.
docker compose logs --tail=50
echo.
echo  常见原因：
echo    - 3306 端口被占用（本地是不是还开着另一个 MySQL？）
echo    - 镜像还没拉下来（往上翻有没有 pull 的错误）
echo.
pause
exit /b 1
