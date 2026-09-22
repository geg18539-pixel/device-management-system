# CI/CD 与部署说明

这套流水线做三件事：**每次改动自动检查能不能构建**、**构建好两个 Docker 镜像推到 GitHub**、
**你在任意一台装了 Docker 的机器上一条命令拉到最新版本**。

代码仓库：https://github.com/geg18539-pixel/device-management-system

---

## 一、整条链路

```
你 git push
    │
    ├─► ci.yml        后端 mvn verify + 前端 pnpm build      ← 几分钟，每次 push 都跑
    │                 只是「检查」，不产出任何东西
    │
    └─► docker.yml    构建 backend / frontend 两个镜像
                      推到 ghcr.io（GitHub 自带的容器仓库）   ← 只在你合到 main 或打 v* tag 时跑
                            │
                            ▼
                      你自己的机器上
                      docker compose pull && up -d
```

「检查」和「发布」是分开的两个工作流，理由很实际：检查要便宜、每次都跑才不心疼；
做镜像慢得多（要装依赖、编译、打包），只有真需要发版时才值得做。

---

## 二、两个工作流分别在什么时候跑

| 工作流 | 触发时机 | 干什么 | 产出 |
|---|---|---|---|
| `CI` | 任何分支的 push，以及 PR | 后端 `mvn verify`（编译 + 跑测试）、前端 `pnpm install --frozen-lockfile` + `pnpm build`（类型检查 + 打包） | 没有产出，只有红/绿 |
| `构建并推送镜像` | push 到 `main`、打 `v*` 标签、或在 Actions 页面手动点 | 构建两个镜像推到 GHCR | `ghcr.io/geg18539-pixel/device-management-system/backend` 和 `.../frontend` |

两个工作流都配了「同一个分支连推时取消上一次」，所以连着提交不会让几次构建排队浪费额度。

**想让某个改动的镜像带版本号**，打个标签就行：

```bash
git tag v1.2.0
git push origin v1.2.0
```

会额外生成 `1.2.0` 和 `1.2` 两个标签。平常不用打，`main` 上的构建会自动带 `latest`
和一个按 commit 区分的 sha 标签。

---

## 三、首次部署要做三件事

### 1. 等第一次镜像构建完成

把代码推上去之后，去 Actions 页面看「构建并推送镜像」有没有跑成功。第一次比较慢
（要下 Maven 依赖和 pnpm 依赖）。跑完之后在这里能看到两个包：

https://github.com/geg18539-pixel/device-management-system/pkgs/container/device-management-system%2Fbackend

### 2. 登录 GHCR

**新推上去的包默认是私有的**，直接 `docker compose pull` 会报 `unauthorized`。
需要生成一个 GitHub 令牌（PAT）：

- 地址：https://github.com/settings/tokens
- 权限勾 **`read:packages`** 就够了，不要勾 `write:packages`
- 然后用它登录：

```bash
docker login ghcr.io
# 用户名填 geg18539-pixel（不是邮箱）
# 密码填刚才那个令牌（不是你的 GitHub 登录密码）
```

Windows 上直接双击 `deploy\更新到最新镜像.bat` 也行 —— 它拉取失败时会引导你登录。

> 如果你想让镜像公开、省掉登录这一步，去上面那个包页面的设置里把可见性改成 public。
> 但仓库里带着 JWT 密钥的配置，公开镜像前先想清楚。

### 3. 改掉 JWT 密钥

`application.yml` 里那个 JWT 密钥只适合本地开发。部署到能被人访问到的机器上，
**必须换一个 32 字节以上的随机串**，否则拿到密钥的人可以自己签发任意用户（含超管）的 token。

在项目根目录建一个 `.env` 文件（这个文件已经被 `.gitignore` 排除，不会提交）：

```ini
JWT_SECRET=换成一串你自己的随机字符，至少32字节
MYSQL_ROOT_PASSWORD=换成你自己的数据库密码
```

---

## 四、日常更新

改完代码，提交推送：

```bash
git add -A
git commit -m "你的说明"
git push
```

等 Actions 里「构建并推送镜像」跑绿之后，在跑服务的机器上双击
`deploy\更新到最新镜像.bat`（Linux 上是 `bash deploy/deploy.sh`），
它会做 `docker compose pull` + `docker compose up -d`。

---

## 五、回滚

每次构建都会打一个 sha 标签，所以任意一次提交的镜像都还在。改
`docker-compose.yml` 里两个 `image:` 的标签，把 `latest` 换成对应的 sha：

```yaml
image: ghcr.io/geg18539-pixel/device-management-system/backend:sha-abc1234...
```

完整的 sha 标签在包的版本列表里能看到。然后重新 `docker compose pull && up -d`。

---

## 六、出问题时怎么查

| 现象 | 多半是什么 |
|---|---|
| CI 里后端作业报连不上数据库 | `backend/backend/src/test/resources/application-test.yml` 没提交上去。CI 靠它把测试的数据库换成 H2，缺了它就会去连 MySQL |
| CI 里前端作业报 lockfile 不一致 | 本地 `pnpm add` 了依赖但忘了提交 `pnpm-lock.yaml`。跑一次 `pnpm install` 再提交 |
| `docker compose pull` 报 `unauthorized` | 没登录 GHCR，见上面第三步 |
| `docker compose pull` 报 `no such manifest` | 镜像还没构建过，去 Actions 页面看「构建并推送镜像」的状态 |
| 容器起来但前端 502 | 后端没起来。`docker compose logs backend` 看日志 |
| AI 功能全部连不上 | Ollama 跑在宿主机上，容器要用 `host.docker.internal`。Linux 上原生 Docker 需要给 backend 加 `extra_hosts: ["host.docker.internal:host-gateway"]` |
| 改了 `AI_*` 环境变量但没生效 | 去系统设置页看是不是库里有值把它盖掉了（库里有值 = 覆盖环境变量） |
| `docker.yml` 报 `denied: permission_denied` | 仓库的 Workflow permissions 是只读。去 Settings → Actions → General → Workflow permissions 改成 **Read and write**，然后再手动跑一次 |

---

## 七、几个刻意的设计决定

**为什么用 GHCR 而不是 Docker Hub。** 用 GHCR 走的是 Actions 自动注入的
`GITHUB_TOKEN`，你不用去建 token、配 secret，权限和可见性跟着仓库走。
Docker Hub 要额外建账号、配两个 secret，而且免费额度对私有仓库有限制。

**为什么 `docker-compose.yml` 里 `image:` 和 `build:` 同时写。**
只写 `build` 的话，在别的机器上拉不到你构建好的镜像；只写 `image` 的话，
本地改完代码没法直接构建来测。两个都写才能既支持「拉现成的」又支持「本地自己构建」。

**为什么 `ci.yml` 里的 `mvn verify` 不跳过测试。** 那个测试看着是空的
（`contextLoads`，没有任何断言），但它启动的是**完整的应用上下文**，
能抓到编译阶段发现不了的一整类问题：实体映射错了、Bean 装配不起来、
配置项名字写错、种子数据跑不完。跳过它等于把 CI 降级成「能编译就行」。

**为什么 Docker 构建的缓存单独配了 scope。** 两个 Dockerfile 都是多阶段的，
最慢的一步是装依赖。缓存命中时重建从十几分钟降到一两分钟。两个镜像的缓存
必须分开（`scope=backend` / `scope=frontend`），否则会互相覆盖。
