package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.DepartmentMapper;
import org.javaboy.vhr.model.Department;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepartmentService 部门服务测试")
class DepartmentServiceTest {

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private DepartmentService departmentService;

    private Department rootDep;
    private Department childDep;

    @BeforeEach
    void setUp() {
        rootDep = new Department("总办");
        rootDep.setId(1);
        rootDep.setParentId(-1);
        rootDep.setDepPath(".1");
        rootDep.setEnabled(true);
        rootDep.setParent(true);
        rootDep.setDirectEmployeeCount(5);
        rootDep.setTotalEmployeeCount(20);

        childDep = new Department("技术部");
        childDep.setId(2);
        childDep.setParentId(1);
        childDep.setDepPath(".1.2");
        childDep.setEnabled(true);
        childDep.setParent(false);
        childDep.setDirectEmployeeCount(15);
        childDep.setTotalEmployeeCount(15);

        rootDep.setChildren(Collections.singletonList(childDep));
    }

    // ==================== 部门树查询测试 ====================

    @Nested
    @DisplayName("getAllDepartments - 部门树查询")
    class GetAllDepartmentsTests {

        @Test
        @DisplayName("部门树应返回 directEmployeeCount 和 totalEmployeeCount 字段")
        void shouldReturnTreeWithEmployeeCounts() {
            when(departmentMapper.getAllDepartmentsByParentId(-1))
                    .thenReturn(Collections.singletonList(rootDep));

            List<Department> tree = departmentService.getAllDepartments();

            assertNotNull(tree);
            assertEquals(1, tree.size());

            Department root = tree.get(0);
            assertEquals(5, root.getDirectEmployeeCount());
            assertEquals(20, root.getTotalEmployeeCount());
            assertEquals(true, root.getEnabled());

            // 验证子节点也包含员工统计
            Department child = root.getChildren().get(0);
            assertEquals(15, child.getDirectEmployeeCount());
            assertEquals(15, child.getTotalEmployeeCount());
        }

        @Test
        @DisplayName("空部门树应返回空列表")
        void shouldReturnEmptyListWhenNoDepartments() {
            when(departmentMapper.getAllDepartmentsByParentId(-1))
                    .thenReturn(Collections.emptyList());

            List<Department> tree = departmentService.getAllDepartments();

            assertNotNull(tree);
            assertTrue(tree.isEmpty());
        }

        @Test
        @DisplayName("部门树应包含 enabled 字段")
        void shouldReturnTreeWithEnabledField() {
            Department disabledDep = new Department("已禁用部门");
            disabledDep.setId(3);
            disabledDep.setEnabled(false);
            disabledDep.setDirectEmployeeCount(0);
            disabledDep.setTotalEmployeeCount(0);

            when(departmentMapper.getAllDepartmentsByParentId(-1))
                    .thenReturn(Arrays.asList(rootDep, disabledDep));

            List<Department> tree = departmentService.getAllDepartments();

            assertEquals(2, tree.size());
            assertTrue(tree.get(0).getEnabled());
            assertFalse(tree.get(1).getEnabled());
        }
    }

    // ==================== 启用/禁用部门测试 ====================

    @Nested
    @DisplayName("updateEnabled - 启用/禁用部门")
    class UpdateEnabledTests {

        @Test
        @DisplayName("启用部门应成功")
        void shouldEnableDepartmentSuccessfully() {
            Department disabledDep = new Department("禁用部门");
            disabledDep.setId(3);
            disabledDep.setEnabled(false);

            when(departmentMapper.selectByPrimaryKey(3)).thenReturn(disabledDep);
            when(departmentMapper.updateEnabled(3, true)).thenReturn(1);

            int result = departmentService.updateEnabled(3, true);

            assertEquals(1, result);
            verify(departmentMapper).updateEnabled(3, true);
        }

        @Test
        @DisplayName("禁用无员工的部门应成功")
        void shouldDisableDepartmentWithNoEmployees() {
            when(departmentMapper.selectByPrimaryKey(2)).thenReturn(childDep);
            when(departmentMapper.getSubtreeEmployeeCount(2)).thenReturn(0);
            when(departmentMapper.updateEnabled(2, false)).thenReturn(1);

            int result = departmentService.updateEnabled(2, false);

            assertEquals(1, result);
            verify(departmentMapper).updateEnabled(2, false);
        }

        @Test
        @DisplayName("禁用有员工的部门应返回 -1 并给出明确错误信息")
        void shouldRejectDisablingDepartmentWithEmployees() {
            when(departmentMapper.selectByPrimaryKey(2)).thenReturn(childDep);
            when(departmentMapper.getSubtreeEmployeeCount(2)).thenReturn(15);

            int result = departmentService.updateEnabled(2, false);

            assertEquals(-1, result);
            // 验证未调用 updateEnabled（即数据库未被修改）
            verify(departmentMapper, never()).updateEnabled(anyInt(), anyBoolean());
        }

        @Test
        @DisplayName("禁用子部门树下有员工的部门应返回 -1")
        void shouldRejectDisablingWhenSubtreeHasEmployees() {
            // rootDep 自身 5 人 + 子部门 15 人 = 20 人
            when(departmentMapper.selectByPrimaryKey(1)).thenReturn(rootDep);
            when(departmentMapper.getSubtreeEmployeeCount(1)).thenReturn(20);

            int result = departmentService.updateEnabled(1, false);

            assertEquals(-1, result);
            verify(departmentMapper, never()).updateEnabled(anyInt(), anyBoolean());
        }

        @Test
        @DisplayName("操作不存在的部门应返回 0")
        void shouldReturnZeroForNonExistentDepartment() {
            when(departmentMapper.selectByPrimaryKey(999)).thenReturn(null);

            int result = departmentService.updateEnabled(999, false);

            assertEquals(0, result);
            verify(departmentMapper, never()).updateEnabled(anyInt(), anyBoolean());
            verify(departmentMapper, never()).getSubtreeEmployeeCount(anyInt());
        }

        @Test
        @DisplayName("启用部门时不需要检查员工数量")
        void shouldNotCheckEmployeeCountWhenEnabling() {
            when(departmentMapper.selectByPrimaryKey(1)).thenReturn(rootDep);
            when(departmentMapper.updateEnabled(1, true)).thenReturn(1);

            int result = departmentService.updateEnabled(1, true);

            assertEquals(1, result);
            // 启用时不应查询员工数量
            verify(departmentMapper, never()).getSubtreeEmployeeCount(anyInt());
        }
    }
}
