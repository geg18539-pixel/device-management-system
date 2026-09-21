import { useUserStore } from '../stores/user'

/**
 * 按钮级权限判断，给模板里配合 `v-if` 用。
 *
 * <pre>
 *   const { hasPerm } = usePerm()
 *   &lt;el-button v-if="hasPerm('sys:user:add')"&gt;新增&lt;/el-button&gt;
 *   &lt;!-- 数组表示"有其中任意一个即可" --&gt;
 *   &lt;el-button v-if="hasPerm(['sys:user:edit', 'sys:user:add'])"&gt;保存&lt;/el-button&gt;
 * </pre>
 *
 * <h3>⚠️ 为什么用 v-if 而不是自定义指令</h3>
 *
 * 一开始这里是个 `v-perm` 指令，在 `mounted` 里把无权限的元素 `removeChild` 掉。
 * 那个做法在 Vue 里是**不可靠**的，而且踩了一个很隐蔽的坑：
 *
 * **自定义指令挂不到多根节点的组件上。** Vue 在 `renderComponentRoot` 里明确写着：
 * <pre>
 *   if (vnode.dirs) {
 *     if (!isElementRoot(root)) {
 *       warn('Runtime directive used on component with non-element root node. ...')
 *     }
 *     root = cloneVNode(root, ...); root.dirs = vnode.dirs   // 指令被塞到根 vnode 上
 *   }
 * </pre>
 * 根节点是 Fragment 时，指令拿到的 `el` 是 Vue 的**锚点注释节点**而不是真实元素。
 * `removeChild` 删掉那个锚点之后，Vue 下一次 patch 找不到插入位置，组件就乱了。
 *
 * 而 **Element Plus 的 el-button 和 el-dropdown-item 根节点都是 Fragment** ——
 * 也就是说指令在它们身上从来就没正确工作过。
 * 更糟的是这个问题**只在浏览器里暴露**：`vue-tsc` 和 `vite build` 都发现不了，
 * 而且不报错，只是"按钮点了没反应"。
 *
 * `v-if` 是 Vue 原生的条件渲染，任何组件都能用，也没有绕过 Vue 的 DOM 更新机制。
 * 代价只是模板里多写一点字，换来的是"一定正确"。
 *
 * <p><b>⚠️ 这不是安全边界</b>，只是界面效果。真正拦住越权的是后端的 `@RequirePerm`。
 */
export function usePerm() {
  const userStore = useUserStore()

  /**
   * 是否拥有某个权限点。
   *
   * <p>超管整体放行，和后端 JwtInterceptor 的语义保持一致 ——
   * 不加这条的话，管理员会因为"新增权限点后忘了给自己勾上"而看不到新按钮。
   */
  function hasPerm(perm: string | string[]): boolean {
    return userStore.hasPerm(perm)
  }

  return { hasPerm }
}
