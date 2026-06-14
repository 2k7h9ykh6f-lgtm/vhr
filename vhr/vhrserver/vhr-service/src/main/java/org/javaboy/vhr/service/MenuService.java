package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.MenuMapper;
import org.javaboy.vhr.mapper.MenuRoleMapper;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.model.Menu;
import org.javaboy.vhr.model.MenuRole;
import org.javaboy.vhr.model.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-09-27 7:13
 */
@Service
@CacheConfig(cacheNames = "menus_cache")
public class MenuService {
    @Autowired
    MenuMapper menuMapper;
    @Autowired
    MenuRoleMapper menuRoleMapper;
    public List<Menu> getMenusByHrId() {
        return menuMapper.getMenusByHrId(((Hr) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }

    @Cacheable
    public List<Menu> getAllMenusWithRole() {
        return menuMapper.getAllMenusWithRole();
    }

    public List<Menu> getAllMenus() {
        return menuMapper.getAllMenus();
    }

    public List<Integer> getMidsByRid(Integer rid) {
        return menuMapper.getMidsByRid(rid);
    }

    @CacheEvict(allEntries = true)
    @Transactional
    public boolean updateMenuRole(Integer rid, Integer[] mids) {
        menuRoleMapper.deleteByRid(rid);
        if (mids == null || mids.length == 0) {
            return true;
        }
        Integer result = menuRoleMapper.insertRecord(rid, mids);
        return result==mids.length;
    }

    /**
     * 统计当前菜单-角色绑定情况，供权限配置接口在刷新缓存后返回。
     * 此处读取 getAllMenusWithRole()，由于调用方刚刚清空了 menus_cache，
     * 因此拿到的是最新数据，避免返回与权限拦截器不一致的旧快照。
     * 返回：权限拦截器需遍历的菜单数量(menuCount)、被绑定到菜单的角色数量(roleCount)，
     * 以及每个角色绑定的菜单数量(roleBindings)。空菜单授权(角色被清空)的菜单仍计入 menuCount，但不产生绑定。
     */
    public Map<String, Object> getMenuRoleSummary() {
        List<Menu> menus = getAllMenusWithRole();
        Map<String, Integer> roleBindings = new LinkedHashMap<>();
        if (menus != null) {
            for (Menu menu : menus) {
                List<Role> roles = menu.getRoles();
                if (roles == null) {
                    continue;
                }
                for (Role role : roles) {
                    roleBindings.merge(role.getName(), 1, Integer::sum);
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("menuCount", menus == null ? 0 : menus.size());
        summary.put("roleCount", roleBindings.size());
        summary.put("roleBindings", roleBindings);
        return summary;
    }
}
