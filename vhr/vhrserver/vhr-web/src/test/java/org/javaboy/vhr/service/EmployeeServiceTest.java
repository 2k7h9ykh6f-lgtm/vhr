package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.EmployeeMapper;
import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.RespPageBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EmployeeService 单元测试 — 验证增强的员工查询功能：
 * 1. contractRemainingDays 计算逻辑
 * 2. 新增筛选参数（beginContractEndDate、endContractEndDate、contractExpireWithinDays）透传
 * 3. 原有 page/size 和 beginDateScope 行为不受影响
 */
@ExtendWith(MockitoExtension.class)
public class EmployeeServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee empWithContract;
    private Employee empWithoutContract;

    @BeforeEach
    void setUp() {
        // 合同将于 30 天后到期的员工
        empWithContract = new Employee();
        empWithContract.setId(1);
        empWithContract.setName("张三");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 30);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        empWithContract.setEndContract(cal.getTime());

        // 没有合同到期日的员工
        empWithoutContract = new Employee();
        empWithoutContract.setId(2);
        empWithoutContract.setName("李四");
        empWithoutContract.setEndContract(null);
    }

    // ============== contractRemainingDays 计算 ==============

    @Test
    @DisplayName("getEmployeeByPage: 应正确计算合同剩余天数")
    void shouldComputeContractRemainingDays() {
        List<Employee> mockData = Arrays.asList(empWithContract, empWithoutContract);
        when(employeeMapper.getEmployeeByPage(anyInt(), anyInt(), any(Employee.class), any()))
                .thenReturn(mockData);
        when(employeeMapper.getTotal(any(Employee.class), any())).thenReturn(2L);

        RespPageBean result = employeeService.getEmployeeByPage(1, 10, new Employee(), null);

        @SuppressWarnings("unchecked")
        List<Employee> data = (List<Employee>) result.getData();
        assertEquals(2, data.size());

        // 有合同到期日的员工：剩余天数应约等于 30 天（允许 ±1 天的误差，因时区/时间戳精度）
        Employee zhangsan = data.get(0);
        assertNotNull(zhangsan.getContractRemainingDays());
        assertTrue(Math.abs(zhangsan.getContractRemainingDays() - 30) <= 1,
                "合同剩余天数应约为 30 天，实际: " + zhangsan.getContractRemainingDays());

        // 没有合同到期日的员工：contractRemainingDays 应为 null
        Employee lisi = data.get(1);
        assertNull(lisi.getContractRemainingDays(),
                "没有合同到期日的员工，contractRemainingDays 应为 null");
    }

    @Test
    @DisplayName("getEmployeeByPage: 合同已过期的员工，剩余天数应为负数")
    void shouldReturnNegativeDaysForExpiredContract() {
        Employee expiredEmp = new Employee();
        expiredEmp.setId(3);
        expiredEmp.setName("王五");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -10); // 10 天前就到期了
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        expiredEmp.setEndContract(cal.getTime());

        when(employeeMapper.getEmployeeByPage(anyInt(), anyInt(), any(Employee.class), any()))
                .thenReturn(Collections.singletonList(expiredEmp));
        when(employeeMapper.getTotal(any(Employee.class), any())).thenReturn(1L);

        RespPageBean result = employeeService.getEmployeeByPage(1, 10, new Employee(), null);

        @SuppressWarnings("unchecked")
        List<Employee> data = (List<Employee>) result.getData();
        Employee wangwu = data.get(0);
        assertNotNull(wangwu.getContractRemainingDays());
        assertTrue(wangwu.getContractRemainingDays() < 0,
                "已过期合同的员工，剩余天数应为负数，实际: " + wangwu.getContractRemainingDays());
        assertTrue(Math.abs(wangwu.getContractRemainingDays() + 10) <= 1,
                "剩余天数应约为 -10 天，实际: " + wangwu.getContractRemainingDays());
    }

    @Test
    @DisplayName("getEmployeeByPage: 空列表不应抛异常")
    void shouldHandleEmptyList() {
        when(employeeMapper.getEmployeeByPage(anyInt(), anyInt(), any(Employee.class), any()))
                .thenReturn(Collections.emptyList());
        when(employeeMapper.getTotal(any(Employee.class), any())).thenReturn(0L);

        RespPageBean result = employeeService.getEmployeeByPage(1, 10, new Employee(), null);

        @SuppressWarnings("unchecked")
        List<Employee> data = (List<Employee>) result.getData();
        assertTrue(data.isEmpty());
        assertEquals(0L, result.getTotal());
    }

    // ============== 原有行为不变 ==============

    @Test
    @DisplayName("getEmployeeByPage: page 应从 1-based 转为 0-based offset")
    void shouldConvertPageToOffset() {
        when(employeeMapper.getEmployeeByPage(eq(20), eq(10), any(Employee.class), any()))
                .thenReturn(Collections.emptyList());
        when(employeeMapper.getTotal(any(Employee.class), any())).thenReturn(0L);

        // page=3, size=10 → offset = (3-1)*10 = 20
        employeeService.getEmployeeByPage(3, 10, new Employee(), null);

        verify(employeeMapper).getEmployeeByPage(eq(20), eq(10), any(Employee.class), any());
    }

    @Test
    @DisplayName("getEmployeeByPage: beginDateScope 应直接透传到 mapper")
    void shouldPassBeginDateScopeToMapper() {
        Date[] scope = new Date[]{new Date(), new Date()};
        Employee emp = new Employee();
        emp.setName("测试");

        when(employeeMapper.getEmployeeByPage(anyInt(), anyInt(), eq(emp), eq(scope)))
                .thenReturn(Collections.emptyList());
        when(employeeMapper.getTotal(eq(emp), eq(scope))).thenReturn(0L);

        employeeService.getEmployeeByPage(1, 10, emp, scope);

        verify(employeeMapper).getEmployeeByPage(anyInt(), anyInt(), eq(emp), eq(scope));
        verify(employeeMapper).getTotal(eq(emp), eq(scope));
    }

    // ============== 新增筛选参数透传 ==============

    @Test
    @DisplayName("getEmployeeByPage: 新增筛选字段应通过 Employee POJO 透传到 mapper")
    void shouldPassNewFilterFieldsToMapper() {
        Employee queryEmp = new Employee();
        queryEmp.setDepartmentId(5);
        queryEmp.setJobLevelId(3);
        queryEmp.setPosId(2);
        queryEmp.setBeginContractEndDate(new Date());
        queryEmp.setEndContractEndDate(new Date());
        queryEmp.setContractExpireWithinDays(90);

        when(employeeMapper.getEmployeeByPage(anyInt(), anyInt(), eq(queryEmp), any()))
                .thenReturn(Collections.emptyList());
        when(employeeMapper.getTotal(eq(queryEmp), any())).thenReturn(0L);

        employeeService.getEmployeeByPage(1, 10, queryEmp, null);

        // 验证 mapper 被调用时接收到完整的 Employee 对象（包含所有新增字段）
        verify(employeeMapper).getEmployeeByPage(anyInt(), anyInt(), argThat(emp -> {
            Employee e = (Employee) emp;
            return Integer.valueOf(5).equals(e.getDepartmentId())
                    && Integer.valueOf(3).equals(e.getJobLevelId())
                    && Integer.valueOf(2).equals(e.getPosId())
                    && e.getBeginContractEndDate() != null
                    && e.getEndContractEndDate() != null
                    && Integer.valueOf(90).equals(e.getContractExpireWithinDays());
        }), any());
    }

    @Test
    @DisplayName("getEmployeeByPage: export 场景（page/size 为 null）不应抛异常")
    void shouldHandleNullPageAndSizeForExport() {
        when(employeeMapper.getEmployeeByPage(isNull(), isNull(), any(Employee.class), isNull()))
                .thenReturn(Arrays.asList(empWithContract));
        when(employeeMapper.getTotal(any(Employee.class), isNull())).thenReturn(1L);

        // 模拟 exportData() 的调用方式
        RespPageBean result = employeeService.getEmployeeByPage(null, null, new Employee(), null);

        @SuppressWarnings("unchecked")
        List<Employee> data = (List<Employee>) result.getData();
        assertEquals(1, data.size());
        // export 场景也应计算合同剩余天数
        assertNotNull(data.get(0).getContractRemainingDays());
    }
}
