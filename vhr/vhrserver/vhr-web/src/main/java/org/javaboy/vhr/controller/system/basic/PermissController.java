package org.javaboy.vhr.controller.system.basic;

import org.javaboy.vhr.model.Menu;
import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.model.Role;
import org.javaboy.vhr.service.MenuService;
import org.javaboy.vhr.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-10-01 19:41
 */
@RestController
@RequestMapping("/system/basic/permiss")
public class PermissController {
    @Autowired
    RoleService roleService;
    @Autowired
    MenuService menuService;

    @GetMapping("/")
    public List<Role> getAllRoles() {
        return roleService.getAllRoles();
    }

    @GetMapping("/menus")
    public List<Menu> getAllMenus() {
        return menuService.getAllMenus();
    }

    @GetMapping("/mids/{rid}")
    public List<Integer> getMidsByRid(@PathVariable Integer rid) {
        return menuService.getMidsByRid(rid);
    }

    @PutMapping("/")
    public RespBean updateMenuRole(Integer rid, Integer[] mids) {
        if (menuService.updateMenuRole(rid, mids)) {
            // 缓存已由 @CacheEvict 自动刷新，此处查询最新数据返回摘要
            List<Menu> latestMenus = menuService.getAllMenusWithRole();
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalMenus", latestMenus.size());
            summary.put("roleMenuCount", mids != null ? mids.length : 0);
            summary.put("roleId", rid);
            summary.put("cacheRefreshed", true);
            return RespBean.ok("更新成功!", summary);
        }
        return RespBean.error("更新失败!");
    }

    @PostMapping("/role")
    public RespBean addRole(@RequestBody Role role) {
        int result = roleService.addRole(role);
        if (result == 1) {
            List<Role> allRoles = roleService.getAllRoles();
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalRoles", allRoles.size());
            summary.put("addedRoleName", role.getName());
            summary.put("cacheRefreshed", true);
            return RespBean.ok("添加成功!", summary);
        }
        return RespBean.error("添加失败!");
    }

    @DeleteMapping("/role/{rid}")
    public RespBean deleteRoleById(@PathVariable Integer rid) {
        if (roleService.deleteRoleById(rid) == 1) {
            List<Role> allRoles = roleService.getAllRoles();
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalRoles", allRoles.size());
            summary.put("deletedRoleId", rid);
            summary.put("cacheRefreshed", true);
            return RespBean.ok("删除成功!", summary);
        }
        return RespBean.error("删除失败!");
    }
}
