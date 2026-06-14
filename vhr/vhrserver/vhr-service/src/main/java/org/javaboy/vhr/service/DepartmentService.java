package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.DepartmentMapper;
import org.javaboy.vhr.model.Department;
import org.javaboy.vhr.model.RespBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-10-21 8:04
 */
@Service
public class DepartmentService {
    @Autowired
    DepartmentMapper departmentMapper;
    public List<Department> getAllDepartments() {
        List<Department> departments = departmentMapper.getAllDepartmentsByParentId(-1);
        for (Department department : departments) {
            computeTotalEmployeeCount(department);
        }
        return departments;
    }

    /**
     * 递归累加部门子树的员工数：totalEmployeeCount = 本部门直属员工数 + 所有子部门的 totalEmployeeCount。
     * directEmployeeCount 由查询直接返回，本方法只负责自底向上的汇总。
     */
    private int computeTotalEmployeeCount(Department department) {
        int total = department.getDirectEmployeeCount() == null ? 0 : department.getDirectEmployeeCount();
        List<Department> children = department.getChildren();
        if (children != null) {
            for (Department child : children) {
                total += computeTotalEmployeeCount(child);
            }
        }
        department.setTotalEmployeeCount(total);
        return total;
    }

    public void addDep(Department dep) {
        dep.setEnabled(true);
        departmentMapper.addDep(dep);
    }

    public void deleteDepById(Department dep) {
        departmentMapper.deleteDepById(dep);
    }

    /**
     * 启用 / 禁用部门。禁用时若该部门下仍有直属员工，则拒绝操作并返回明确的错误信息。
     */
    public RespBean setDepEnableById(Integer id, Boolean enabled) {
        if (Boolean.FALSE.equals(enabled)) {
            Integer employeeCount = departmentMapper.getEmployeeCountByDepId(id);
            if (employeeCount != null && employeeCount > 0) {
                return RespBean.error("该部门下有 " + employeeCount + " 名员工，无法禁用");
            }
        }
        Department dep = new Department();
        dep.setId(id);
        dep.setEnabled(enabled);
        departmentMapper.updateDepEnableById(dep);
        return RespBean.ok(Boolean.FALSE.equals(enabled) ? "禁用成功" : "启用成功");
    }

    public List<Department> getAllDepartmentsWithOutChildren() {
        return departmentMapper.getAllDepartmentsWithOutChildren();
    }
}
