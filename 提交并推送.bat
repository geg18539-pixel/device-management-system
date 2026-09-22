@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo.
echo  ============================================================
echo   提交并推送到 GitHub
echo  ============================================================
echo.
echo  这个脚本做三件事：暂存全部改动 -^> 提交 -^> 推送
echo.

echo  [1/3] 暂存改动：
echo.
git add -A
git -c core.quotepath=false status --short
echo.

set "MSG=前端改版收尾 + 后台概览页；补齐 CI/CD 与部署脚本"
set "INPUT="
set /p "INPUT=提交说明（直接回车用上面那句默认的）："
if defined INPUT set "MSG=%INPUT%"

echo.
echo  [2/3] 提交...
git commit -m "%MSG%"
if errorlevel 1 goto fail_commit

echo.
echo  [3/3] 推送到 origin/main...
git push
if errorlevel 1 goto fail_push

echo.
echo  完成。流水线应该已经开始跑了，去这里看：
echo    https://github.com/geg18539-pixel/device-management-system/actions
echo.
echo  等「构建并推送镜像」跑绿之后，双击 deploy\更新到最新镜像.bat 就能更新到你机器上。
echo.
pause
exit /b 0


:fail_commit
echo.
echo  [失败] 提交没有成功，看上面的报错。
echo.
echo  如果提示 "index.lock: File exists"，说明有个残留的锁文件，删掉再重试：
echo      del /f .git\index.lock
echo.
echo  如果提示 "Please tell me who you are"，先设一次身份：
echo      git config --global user.name "geg18539-pixel"
echo      git config --global user.email "geg18539@gmail.com"
echo.
pause
exit /b 1


:fail_push
echo.
echo  [失败] 推送没有成功。
echo.
echo  常见原因：
echo    1. 需要登录 GitHub —— 一般会弹窗让你登录，照做即可
echo    2. 远端有你本地没有的提交（比如你在网页上改过文件）
echo       先跑 git pull --rebase origin main 再重试
echo.
pause
exit /b 1
