package org.javaboy.vhr;

import org.javaboy.vhr.mapper.EmployeeMapper;
import org.javaboy.vhr.mapper.SalaryChangeLogMapper;
import org.javaboy.vhr.mapper.SalaryMapper;
import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.model.Salary;
import org.javaboy.vhr.model.SalaryChangeLog;
import org.javaboy.vhr.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
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
 * 批量设置薪资账套的单元测试。
 * 纯 Mockito，不加载 Spring 上下文，因此无需 MySQL/RabbitMQ/Redis 即可运行。
 */
@ExtendWith(MockitoExtension.class)
public class EmployeeServiceTest {

    @Mock
    EmployeeMapper employeeMapper;
    @Mock
    SalaryMapper salaryMapper;
    @Mock
    SalaryChangeLogMapper salaryChangeLogMapper;

    @InjectMocks
    EmployeeService employeeService;

    @Test
    @SuppressWarnings("unchecked")
    public void batchUpdate_allSuccess() {
        Integer sid = 9;
        when(salaryMapper.selectByPrimaryKey(sid)).thenReturn(new Salary());
        when(employeeMapper.selectByPrimaryKey(anyInt())).thenReturn(new Employee());
        when(employeeMapper.getSalaryIdByEid(anyInt())).thenReturn(5);
        when(employeeMapper.updateEmployeeSalaryById(anyInt(), eq(sid))).thenReturn(1);

        RespBean resp = employeeService.batchUpdateEmployeeSalary(Arrays.asList(1, 2, 3), sid, "admin");

        assertEquals(200, resp.getStatus().intValue());
        Map<String, Object> data = (Map<String, Object>) resp.getObj();
        assertEquals(3, ((Integer) data.get("successCount")).intValue());
        assertTrue(((List<Integer>) data.get("notFound")).isEmpty());
        verify(employeeMapper, times(3)).updateEmployeeSalaryById(anyInt(), eq(sid));
        verify(salaryChangeLogMapper, times(3)).insert(any(SalaryChangeLog.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void batchUpdate_partialNotFound() {
        Integer sid = 9;
        when(salaryMapper.selectByPrimaryKey(sid)).thenReturn(new Salary());
        // eid 1 与 3 存在，eid 2 不存在
        when(employeeMapper.selectByPrimaryKey(1)).thenReturn(new Employee());
        when(employeeMapper.selectByPrimaryKey(2)).thenReturn(null);
        when(employeeMapper.selectByPrimaryKey(3)).thenReturn(new Employee());
        when(employeeMapper.getSalaryIdByEid(anyInt())).thenReturn(null);
        when(employeeMapper.updateEmployeeSalaryById(anyInt(), eq(sid))).thenReturn(1);

        RespBean resp = employeeService.batchUpdateEmployeeSalary(Arrays.asList(1, 2, 3), sid, "admin");

        assertEquals(200, resp.getStatus().intValue());
        Map<String, Object> data = (Map<String, Object>) resp.getObj();
        assertEquals(2, ((Integer) data.get("successCount")).intValue());
        List<Integer> notFound = (List<Integer>) data.get("notFound");
        assertEquals(1, notFound.size());
        assertTrue(notFound.contains(2));
        verify(employeeMapper, never()).updateEmployeeSalaryById(eq(2), anyInt());
        verify(salaryChangeLogMapper, times(2)).insert(any(SalaryChangeLog.class));
    }

    @Test
    public void batchUpdate_salaryNotExist() {
        Integer sid = 999;
        when(salaryMapper.selectByPrimaryKey(sid)).thenReturn(null);

        RespBean resp = employeeService.batchUpdateEmployeeSalary(Arrays.asList(1, 2), sid, "admin");

        assertEquals(500, resp.getStatus().intValue());
        assertEquals("薪资账套不存在", resp.getMsg());
        verify(employeeMapper, never()).updateEmployeeSalaryById(anyInt(), anyInt());
        verify(salaryChangeLogMapper, never()).insert(any(SalaryChangeLog.class));
    }
}
