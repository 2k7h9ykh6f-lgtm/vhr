package org.javaboy.vhr;

import org.javaboy.vhr.mapper.MenuMapper;
import org.javaboy.vhr.mapper.MenuRoleMapper;
import org.javaboy.vhr.mapper.RoleMapper;
import org.javaboy.vhr.model.Menu;
import org.javaboy.vhr.model.Role;
import org.javaboy.vhr.service.MenuService;
import org.javaboy.vhr.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证角色-菜单关系变更后 menus_cache 会被刷新，从而保证
 * {@link org.javaboy.vhr.config.CustomFilterInvocationSecurityMetadataSource}
 * 在下一次请求时读取到最新的菜单角色关系（修复"权限修改不能立即生效"的问题）。
 *
 * <p>测试使用进程内的 {@link ConcurrentMapCacheManager} 而非运行时的 Redis，
 * 因为 {@code @CacheEvict}/{@code @Cacheable} 的行为与缓存实现无关，
 * 这样无需 Redis/MySQL 即可独立验证缓存刷新逻辑。</p>
 *
 * <p>覆盖场景：缓存命中、updateMenuRole 刷新缓存、角色新增/删除刷新缓存、
 * 空菜单授权（mids 为 null 或空数组）仍刷新缓存且不执行插入、以及角色绑定摘要的统计。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PermissCacheTest.CachingTestConfig.class)
public class PermissCacheTest {

    @Configuration
    @EnableCaching
    static class CachingTestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("menus_cache");
        }

        @Bean
        public MenuService menuService() {
            return new MenuService();
        }

        @Bean
        public RoleService roleService() {
            return new RoleService();
        }

        @Bean
        public MenuMapper menuMapper() {
            return Mockito.mock(MenuMapper.class);
        }

        @Bean
        public MenuRoleMapper menuRoleMapper() {
            return Mockito.mock(MenuRoleMapper.class);
        }

        @Bean
        public RoleMapper roleMapper() {
            return Mockito.mock(RoleMapper.class);
        }
    }

    @Autowired
    MenuService menuService;
    @Autowired
    RoleService roleService;
    @Autowired
    MenuMapper menuMapper;
    @Autowired
    MenuRoleMapper menuRoleMapper;
    @Autowired
    RoleMapper roleMapper;
    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    public void setUp() {
        // 每个用例之间清空缓存与 mock 计数，保证用例相互独立。
        cacheManager.getCache("menus_cache").clear();
        Mockito.reset(menuMapper, menuRoleMapper, roleMapper);
    }

    @Test
    public void getAllMenusWithRole_isCached() {
        when(menuMapper.getAllMenusWithRole()).thenReturn(sampleMenus());

        menuService.getAllMenusWithRole();
        menuService.getAllMenusWithRole();

        // 第二次应命中缓存，底层 mapper 只被调用一次。
        verify(menuMapper, times(1)).getAllMenusWithRole();
    }

    @Test
    public void updateMenuRole_evictsCache() {
        when(menuMapper.getAllMenusWithRole()).thenReturn(sampleMenus());
        when(menuRoleMapper.insertRecord(eq(1), any())).thenReturn(2);

        menuService.getAllMenusWithRole();          // 预热缓存 -> mapper 第 1 次
        menuService.getAllMenusWithRole();          // 命中缓存 -> 仍是 1 次
        verify(menuMapper, times(1)).getAllMenusWithRole();

        boolean ok = menuService.updateMenuRole(1, new Integer[]{1, 2});
        assertTrue(ok);

        menuService.getAllMenusWithRole();          // 缓存已被清空 -> mapper 第 2 次
        verify(menuMapper, times(2)).getAllMenusWithRole();
    }

    @Test
    public void updateMenuRole_withNullMids_evictsCacheAndSkipsInsert() {
        when(menuMapper.getAllMenusWithRole()).thenReturn(sampleMenus());

        menuService.getAllMenusWithRole();          // 预热缓存
        verify(menuMapper, times(1)).getAllMenusWithRole();

        // 空菜单授权：清空某角色的全部菜单。
        boolean ok = menuService.updateMenuRole(1, null);
        assertTrue(ok);
        verify(menuRoleMapper, times(1)).deleteByRid(1);
        verify(menuRoleMapper, never()).insertRecord(anyInt(), any());

        menuService.getAllMenusWithRole();          // 仍需刷新缓存
        verify(menuMapper, times(2)).getAllMenusWithRole();
    }

    @Test
    public void updateMenuRole_withEmptyMids_evictsCacheAndSkipsInsert() {
        when(menuMapper.getAllMenusWithRole()).thenReturn(sampleMenus());

        menuService.getAllMenusWithRole();
        verify(menuMapper, times(1)).getAllMenusWithRole();

        boolean ok = menuService.updateMenuRole(1, new Integer[0]);
        assertTrue(ok);
        verify(menuRoleMapper, times(1)).deleteByRid(1);
        verify(menuRoleMapper, never()).insertRecord(anyInt(), any());

        menuService.getAllMenusWithRole();
        verify(menuMapper, times(2)).getAllMenusWithRole();
    }

    @Test
    public void addRole_evictsMenusCache() {
        when(menuMapper.getAllMenusWithRole()).thenReturn(sampleMenus());
        when(roleMapper.insert(any(Role.class))).thenReturn(1);

        menuService.getAllMenusWithRole();          // 预热缓存
        verify(menuMapper, times(1)).getAllMenusWithRole();

        roleService.addRole(role("ROLE_test"));

        menuService.getAllMenusWithRole();          // 角色新增后缓存应被清空
        verify(menuMapper, times(2)).getAllMenusWithRole();
    }

    @Test
    public void deleteRoleById_evictsMenusCache() {
        when(menuMapper.getAllMenusWithRole()).thenReturn(sampleMenus());
        when(roleMapper.deleteByPrimaryKey(eq(1))).thenReturn(1);

        menuService.getAllMenusWithRole();          // 预热缓存
        verify(menuMapper, times(1)).getAllMenusWithRole();

        roleService.deleteRoleById(1);

        menuService.getAllMenusWithRole();          // 角色删除后缓存应被清空
        verify(menuMapper, times(2)).getAllMenusWithRole();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getMenuRoleSummary_computesCountsAndBindings() {
        List<Menu> menus = new ArrayList<>();
        menus.add(menu("/admin/**", role("ROLE_admin"), role("ROLE_hr")));
        menus.add(menu("/hr/**", role("ROLE_admin")));
        menus.add(menu("/empty/**"));                       // 空菜单授权：roles 为空集合
        Menu nullRoles = menu("/null/**");
        nullRoles.setRoles(null);                            // 防御：roles 为 null
        menus.add(nullRoles);
        when(menuMapper.getAllMenusWithRole()).thenReturn(menus);

        Map<String, Object> summary = menuService.getMenuRoleSummary();

        assertEquals(4, summary.get("menuCount"));           // 含空/ null 角色的菜单仍计入
        assertEquals(2, summary.get("roleCount"));
        Map<String, Integer> bindings = (Map<String, Integer>) summary.get("roleBindings");
        assertEquals(2, bindings.get("ROLE_admin").intValue());
        assertEquals(1, bindings.get("ROLE_hr").intValue());
    }

    private List<Menu> sampleMenus() {
        List<Menu> menus = new ArrayList<>();
        menus.add(menu("/admin/**", role("ROLE_admin")));
        return menus;
    }

    private Menu menu(String url, Role... roles) {
        Menu m = new Menu();
        m.setUrl(url);
        if (roles != null && roles.length > 0) {
            m.setRoles(new ArrayList<>(Arrays.asList(roles)));
        } else {
            m.setRoles(Collections.emptyList());
        }
        return m;
    }

    private Role role(String name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }
}
