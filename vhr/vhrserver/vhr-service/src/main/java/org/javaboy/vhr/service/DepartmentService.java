package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.DepartmentMapper;
import org.javaboy.vhr.model.Department;
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
        return departmentMapper.getAllDepartmentsByParentId(-1);
    }

    public void addDep(Department dep) {
        dep.setEnabled(true);
        departmentMapper.addDep(dep);
    }

    public void deleteDepById(Department dep) {
        departmentMapper.deleteDepById(dep);
    }

    public List<Department> getAllDepartmentsWithOutChildren() {
        return departmentMapper.getAllDepartmentsWithOutChildren();
    }

    /**
     * 启用或禁用部门。
     * 禁用时，若该部门及其所有子部门下存在员工，则拒绝操作并返回 -1。
     *
     * @param id      部门 ID
     * @param enabled 是否启用
     * @return 1 成功, -1 部门下有员工无法禁用, 0 部门不存在
     */
    public int updateEnabled(Integer id, Boolean enabled) {
        Department dep = departmentMapper.selectByPrimaryKey(id);
        if (dep == null) {
            return 0;
        }
        if (Boolean.FALSE.equals(enabled)) {
            int employeeCount = departmentMapper.getSubtreeEmployeeCount(id);
            if (employeeCount > 0) {
                return -1;
            }
        }
        departmentMapper.updateEnabled(id, enabled);
        return 1;
    }
}
