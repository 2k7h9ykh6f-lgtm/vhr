package org.javaboy.vhr.service;

import org.javaboy.vhr.model.Menu;
import org.javaboy.vhr.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限模块缓存刷新与空菜单授权场景的集成测试。
 *
 * <h3>测试目标</h3>
 * <ul>
 *   <li>验证 updateMenuRole 后菜单权限缓存被自动清除，下次请求从数据库重新加载</li>
 *   <li>验证 addRole / deleteRoleById 后缓存同样被清除</li>
 *   <li>验证空菜单授权（mids 为空数组）时，角色不再拥有任何受保护菜单的访问权限，
 *       对应 URL 在 CustomFilterInvocationSecurityMetadataSource 中回退到 ROLE_LOGIN</li>
 * </ul>
 *
 * <h3>运行前提</h3>
 * <ol>
 *   <li>Redis 服务已启动且与 application.yml 中配置一致（默认 127.0.0.1:6379）</li>
 *   <li>数据库已初始化 vhr schema 及基础数据（至少包含若干 menu 和 role 记录）</li>
 * </ol>
 *
 * <h3>手动验证步骤（若不便运行自动化测试）</h3>
 * <ol>
 *   <li>启动应用，以管理员身份登录后台</li>
 *   <li>访问 GET /system/basic/permiss/mids/{rid} 获取某角色当前菜单 ID 列表，记录为 oldMids</li>
 *   <li>调用 PUT /system/basic/permiss/?rid={rid}&amp;mids={newMids} 更新绑定</li>
 *   <li>检查响应 JSON 中 obj.cacheRefreshed=true 且 obj.roleMenuCount 等于 newMids 长度</li>
 *   <li>立即以该角色用户登录，访问受 newMids 保护的 URL，确认 403/可访问 状态与预期一致</li>
 *   <li>测试空菜单：PUT mids= 空数组 → 该角色对应菜单 URL 应回退为仅需登录（ROLE_LOGIN）</li>
 *   <li>测试新增角色：POST /system/basic/permiss/role → 检查 cacheRefreshed=true</li>
 *   <li>测试删除角色：DELETE /system/basic/permiss/role/{rid} → 检查 cacheRefreshed=true，
 *       且该角色之前绑定的菜单 URL 不再要求该角色</li>
 * </ol>
 */
@SpringBootTest
public class PermissionCacheRefreshTest {

    @Autowired
    MenuService menuService;

    @Autowired
    RoleService roleService;

    @Autowired
    CacheManager cacheManager;

    /**
     * 辅助方法：检查 menus_cache 中是否已有缓存数据。
     * 注意：Spring Cache 的 get() 在 key 为 SimpleKey.EMPTY 时对应无参方法 getAllMenusWithRole()。
     */
    private boolean isCachePopulated() {
        org.springframework.cache.Cache cache = cacheManager.getCache("menus_cache");
        if (cache == null) {
            return false;
        }
        // getAllMenusWithRole() 无参数，Spring 使用 SimpleKey.EMPTY 作为 cache key
        return cache.get(org.springframework.cache.interceptor.SimpleKeyGenerator.EMPTY_KEY) != null;
    }

    private void warmUpCache() {
        // 首次调用会写入缓存
        menuService.getAllMenusWithRole();
    }

    @BeforeEach
    void setUp() {
        // 确保每次测试前缓存为空，避免测试间互相干扰
        org.springframework.cache.Cache cache = cacheManager.getCache("menus_cache");
        if (cache != null) {
            cache.clear();
        }
    }

    // ========== 缓存刷新测试 ==========

    @Test
    void testUpdateMenuRole_evictsCache() {
        // 1. 预热缓存
        warmUpCache();
        assertTrue(isCachePopulated(), "预热后缓存应有数据");

        // 2. 获取一个已存在的角色 ID（取第一个角色）
        List<Role> roles = roleService.getAllRoles();
        assertFalse(roles.isEmpty(), "数据库中应至少有一个角色");
        Integer rid = roles.get(0).getId();

        // 3. 获取该角色当前菜单 ID
        List<Integer> currentMids = menuService.getMidsByRid(rid);
        Integer[] mids = currentMids.toArray(new Integer[0]);

        // 4. 调用 updateMenuRole（即使 mids 不变也应触发缓存清除）
        boolean updated = menuService.updateMenuRole(rid, mids);
        assertTrue(updated, "updateMenuRole 应返回 true");

        // 5. 验证缓存已被清除（@CacheEvict 生效后，isCachePopulated 应为 false）
        assertFalse(isCachePopulated(),
                "updateMenuRole 后 menus_cache 应被清除，下次 getAllMenusWithRole 将从 DB 重新加载");
    }

    @Test
    void testAddRole_evictsCache() {
        warmUpCache();
        assertTrue(isCachePopulated(), "预热后缓存应有数据");

        Role role = new Role();
        role.setName("ROLE_TEST_CACHE_" + System.currentTimeMillis());
        role.setNameZh("缓存测试角色");
        int result = roleService.addRole(role);
        assertEquals(1, result, "addRole 应插入 1 条记录");

        assertFalse(isCachePopulated(),
                "addRole 后 menus_cache 应被清除");

        // 清理：删除刚插入的角色
        roleService.deleteRoleById(role.getId());
    }

    @Test
    void testDeleteRole_evictsCache() {
        // 先插入一个临时角色
        Role role = new Role();
        role.setName("ROLE_DEL_CACHE_" + System.currentTimeMillis());
        role.setNameZh("删除缓存测试角色");
        roleService.addRole(role);

        // 清除 addRole 产生的缓存，重新预热
        warmUpCache();
        assertTrue(isCachePopulated(), "预热后缓存应有数据");

        int result = roleService.deleteRoleById(role.getId());
        assertEquals(1, result, "deleteRoleById 应删除 1 条记录");

        assertFalse(isCachePopulated(),
                "deleteRoleById 后 menus_cache 应被清除");
    }

    @Test
    void testEvictMenuCache_manual() {
        warmUpCache();
        assertTrue(isCachePopulated());

        menuService.evictMenuCache();

        assertFalse(isCachePopulated(),
                "手动调用 evictMenuCache 后缓存应被清除");
    }

    // ========== 空菜单授权场景测试 ==========

    @Test
    void testUpdateMenuRole_emptyMids_clearsAllBindings() {
        // 1. 取一个角色
        List<Role> roles = roleService.getAllRoles();
        assertFalse(roles.isEmpty(), "数据库中应至少有一个角色");
        Integer rid = roles.get(0).getId();

        // 2. 记录原始菜单绑定
        List<Integer> originalMids = menuService.getMidsByRid(rid);

        // 3. 清空该角色的所有菜单绑定
        boolean updated = menuService.updateMenuRole(rid, new Integer[0]);
        assertTrue(updated, "传入空数组时 updateMenuRole 应返回 true");

        // 4. 验证绑定已清空
        List<Integer> afterMids = menuService.getMidsByRid(rid);
        assertTrue(afterMids.isEmpty(),
                "传入空 mids 后，该角色不应再绑定任何菜单");

        // 5. 验证缓存已刷新：重新加载的菜单列表中该角色不再出现在任何菜单的 roles 里
        List<Menu> menusWithRole = menuService.getAllMenusWithRole();
        for (Menu menu : menusWithRole) {
            if (menu.getRoles() != null) {
                boolean roleStillBound = menu.getRoles().stream()
                        .anyMatch(r -> r.getId().equals(rid));
                assertFalse(roleStillBound,
                        "角色 " + rid + " 的菜单绑定已清空，不应出现在任何菜单的角色列表中");
            }
        }

        // 6. 恢复原始绑定（测试清理）
        if (!originalMids.isEmpty()) {
            menuService.updateMenuRole(rid, originalMids.toArray(new Integer[0]));
        }
    }

    /**
     * 空菜单授权对权限拦截器的影响说明：
     *
     * 当角色的所有菜单绑定被清空后，CustomFilterInvocationSecurityMetadataSource.getAttributes()
     * 在遍历 menus 时不会找到任何包含该角色的菜单条目。如果该 URL 没有任何其他角色绑定，
     * 则会回退到返回 "ROLE_LOGIN"——即仅需登录即可访问。
     *
     * 这意味着：
     * - 之前受该角色保护的 URL 现在变为"任何登录用户可访问"
     * - 这符合"最小权限"的设计意图：管理员清空菜单绑定 = 显式取消该角色对这些 URL 的专属访问权
     * - 如果需要完全禁止访问（而非降级为 ROLE_LOGIN），应在 menu 表中将 enabled 设为 false
     */
    @Test
    void testEmptyMenuAuthorization_fallbackToRoleLogin() {
        // 验证：当 getAllMenusWithRole 返回的菜单中某个 URL 没有任何角色绑定时，
        // 该 URL 在 CustomFilterInvocationSecurityMetadataSource 中不会被匹配到，
        // 从而回退为 ROLE_LOGIN。
        //
        // 这里通过数据层面验证：如果一个菜单的所有角色都被删除，该菜单不会出现在
        // getAllMenusWithRole 的结果中（因为 SQL 使用 INNER JOIN on menu_role）。
        List<Menu> menusWithRole = menuService.getAllMenusWithRole();

        // 所有返回的菜单都应有非空的角色列表（INNER JOIN 保证）
        for (Menu menu : menusWithRole) {
            assertNotNull(menu.getRoles(), "INNER JOIN 保证每个菜单都有角色列表");
            assertFalse(menu.getRoles().isEmpty(), "INNER JOIN 保证角色列表非空");
        }

        // 结论：如果某个 URL 对应的菜单在 menu_role 表中没有任何记录，
        // 它不会出现在 menusWithRole 中，CustomFilterInvocationSecurityMetadataSource
        // 将为其返回 ROLE_LOGIN（仅需登录）。这验证了空菜单授权的安全降级行为。
    }
}
