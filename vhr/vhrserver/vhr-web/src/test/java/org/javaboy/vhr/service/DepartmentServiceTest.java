package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.DepartmentMapper;
import org.javaboy.vhr.model.Department;
import org.javaboy.vhr.model.RespBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 部门服务单元测试：覆盖部门树员工数统计（directEmployeeCount / totalEmployeeCount）
 * 以及启用/禁用部门时的校验逻辑。使用 Mockito 隔离 DepartmentMapper，无需数据库等外部依赖。
 */
@ExtendWith(MockitoExtension.class)
public class DepartmentServiceTest {

    @Mock
    DepartmentMapper departmentMapper;

    @InjectMocks
    DepartmentService departmentService;

    private Department dep(int id, int directCount, Department... children) {
        Department d = new Department();
        d.setId(id);
        d.setName("dep" + id);
        d.setDirectEmployeeCount(directCount);
        d.setChildren(new ArrayList<>(Arrays.asList(children)));
        return d;
    }

    /**
     * 树形统计：totalEmployeeCount 应等于本部门直属员工数加上所有子部门（含多级）的员工数之和，
     * 同时 directEmployeeCount 应原样保留。
     *
     * 结构：
     *   root(direct=2)
     *     ├─ childA(direct=3)
     *     │     └─ grandchild(direct=5)
     *     └─ childB(direct=0)
     */
    @Test
    public void getAllDepartments_shouldAggregateEmployeeCountsAcrossSubtree() {
        Department grandchild = dep(3, 5);
        Department childA = dep(2, 3, grandchild);
        Department childB = dep(4, 0);
        Department root = dep(1, 2, childA, childB);

        when(departmentMapper.getAllDepartmentsByParentId(-1))
                .thenReturn(new ArrayList<>(Collections.singletonList(root)));

        List<Department> result = departmentService.getAllDepartments();

        assertEquals(1, result.size());
        // 叶子节点：total = direct
        assertEquals(5, grandchild.getTotalEmployeeCount().intValue());
        // childA = 自身3 + grandchild的5
        assertEquals(8, childA.getTotalEmployeeCount().intValue());
        // childB 无子部门且无员工
        assertEquals(0, childB.getTotalEmployeeCount().intValue());
        // root = 自身2 + childA子树8 + childB子树0
        assertEquals(10, root.getTotalEmployeeCount().intValue());
        // directEmployeeCount 原样透传
        assertEquals(2, root.getDirectEmployeeCount().intValue());
        assertEquals(3, childA.getDirectEmployeeCount().intValue());
    }

    /**
     * directEmployeeCount 为 null 时（防御性）应按 0 处理，不应抛出 NPE。
     */
    @Test
    public void getAllDepartments_shouldTreatNullDirectCountAsZero() {
        Department root = new Department();
        root.setId(1);
        root.setChildren(new ArrayList<>());
        // 不设置 directEmployeeCount，保持为 null

        when(departmentMapper.getAllDepartmentsByParentId(-1))
                .thenReturn(new ArrayList<>(Collections.singletonList(root)));

        departmentService.getAllDepartments();

        assertEquals(0, root.getTotalEmployeeCount().intValue());
    }

    /**
     * 禁用校验：当部门下仍有直属员工时，禁用应被拒绝，返回明确的错误信息，且不执行更新。
     */
    @Test
    public void setDepEnableById_shouldRejectDisableWhenDepartmentHasEmployees() {
        when(departmentMapper.getEmployeeCountByDepId(8)).thenReturn(3);

        RespBean resp = departmentService.setDepEnableById(8, false);

        assertEquals(500, resp.getStatus().intValue());
        assertTrue(resp.getMsg().contains("无法禁用"), "错误信息应说明无法禁用");
        assertTrue(resp.getMsg().contains("3"), "错误信息应包含员工数量");
        // 校验失败不应落库
        verify(departmentMapper, never()).updateDepEnableById(any());
    }

    /**
     * 禁用校验：部门下无员工时，禁用应成功并写入 enabled=false。
     */
    @Test
    public void setDepEnableById_shouldDisableWhenNoEmployees() {
        when(departmentMapper.getEmployeeCountByDepId(8)).thenReturn(0);

        RespBean resp = departmentService.setDepEnableById(8, false);

        assertEquals(200, resp.getStatus().intValue());
        assertEquals("禁用成功", resp.getMsg());

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentMapper).updateDepEnableById(captor.capture());
        Department updated = captor.getValue();
        assertEquals(8, updated.getId().intValue());
        assertFalse(updated.getEnabled());
    }

    /**
     * 启用部门：不应做员工数校验，直接写入 enabled=true。
     */
    @Test
    public void setDepEnableById_shouldEnableWithoutEmployeeCheck() {
        RespBean resp = departmentService.setDepEnableById(8, true);

        assertEquals(200, resp.getStatus().intValue());
        assertEquals("启用成功", resp.getMsg());

        // 启用无需校验员工数
        verify(departmentMapper, never()).getEmployeeCountByDepId(any());

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentMapper).updateDepEnableById(captor.capture());
        assertTrue(captor.getValue().getEnabled());
    }
}
