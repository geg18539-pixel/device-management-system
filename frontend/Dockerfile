# ============================================================
# 阶段一：构建
# ============================================================
FROM node:22-alpine AS builder

# 项目用 pnpm，但 package.json 里没有 packageManager 字段，
# corepack 无法自动判断该装哪个版本，所以这里显式指定。
# 如果以后在 package.json 里加了 "packageManager": "pnpm@x.y.z"，
# 这一行就可以删掉，corepack 会自己读。
RUN corepack enable && corepack prepare pnpm@10 --activate

WORKDIR /build

# 先只复制依赖清单，让「装依赖」这一层能命中 Docker 缓存。
# 只要不改依赖，改业务代码重新构建时不会重装。
COPY package.json pnpm-lock.yaml ./

# --frozen-lockfile：严格按 lockfile 安装，一旦 lockfile 和 package.json
# 不一致就直接报错，避免构建出一份和本地不同的依赖树。
RUN pnpm install --frozen-lockfile

COPY . .

# 执行 package.json 里的 build 脚本（vue-tsc -b && vite build），
# 产物落在 /build/dist
RUN pnpm build

# ============================================================
# 阶段二：运行
# ============================================================
FROM nginx:alpine

# 覆盖镜像自带的默认站点配置。
# 注意本文件内容是 server 块，不是完整的 nginx.conf ——
# 主配置 /etc/nginx/nginx.conf 里已经有 include /etc/nginx/conf.d/*.conf，
# 所以放在这里最干净，不用去覆写主配置。
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 只把构建产物拷过去。node_modules、源码、构建工具都不进最终镜像。
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
