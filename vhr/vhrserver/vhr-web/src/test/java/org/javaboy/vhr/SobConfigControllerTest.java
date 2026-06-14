package org.javaboy.vhr;

import org.javaboy.vhr.mapper.EmployeeMapper;
import org.javaboy.vhr.mapper.SalaryChangeRecordMapper;
import org.javaboy.vhr.model.*;
import org.javaboy.vhr.service.EmployeeService;
import org.javaboy.vhr.service.MailSendLogService;
import org.javaboy.vhr.service.SalaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SobConfigControllerTest {

    @Mock
    EmployeeMapper employeeMapper;

    @Mock
    SalaryChangeRecordMapper salaryChangeRecordMapper;

    @Mock
    RabbitTemplate rabbitTemplate;

    @Mock
    MailSendLogService mailSendLogService;

    @Spy
    SalaryService salaryService;

    @InjectMocks
    EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        // Manually inject the mocked mapper into the spy SalaryService
        try {
            java.lang.reflect.Field field = SalaryService.class.getDeclaredField("salaryMapper");
            field.setAccessible(true);
            org.javaboy.vhr.mapper.SalaryMapper salaryMapper = mock(org.javaboy.vhr.mapper.SalaryMapper.class);
            field.set(salaryService, salaryMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testBatchUpdateSuccess() {
        // Arrange
        Integer sid = 10;
        List<Integer> eids = Arrays.asList(1, 2, 3);

        // Mock salary exists
        Salary salary = new Salary();
        salary.setId(sid);
        salary.setName("测试账套");
        org.javaboy.vhr.mapper.SalaryMapper salaryMapper = getSalaryMapper();
        when(salaryMapper.selectByPrimaryKey(sid)).thenReturn(salary);

        // Mock all employees exist
        List<Employee> employees = new ArrayList<>();
        for (Integer eid : eids) {
            Employee emp = new Employee();
            emp.setId(eid);
            employees.add(emp);
        }
        when(employeeMapper.getEmployeeByIds(eids)).thenReturn(employees);

        // Mock current salary bindings
        List<EmpSalary> currentBindings = new ArrayList<>();
        EmpSalary es1 = new EmpSalary();
        es1.setEid(1);
        es1.setSid(9);
        currentBindings.add(es1);
        EmpSalary es2 = new EmpSalary();
        es2.setEid(2);
        es2.setSid(9);
        currentBindings.add(es2);
        // eid=3 has no current binding
        when(employeeMapper.getEmpSalaryByEids(anyList())).thenReturn(currentBindings);

        // Mock update success (REPLACE INTO returns 2 for existing, 1 for new)
        when(employeeMapper.updateEmployeeSalaryById(eq(1), eq(sid))).thenReturn(2);
        when(employeeMapper.updateEmployeeSalaryById(eq(2), eq(sid))).thenReturn(2);
        when(employeeMapper.updateEmployeeSalaryById(eq(3), eq(sid))).thenReturn(1);

        // Mock change record insert
        when(salaryChangeRecordMapper.insertBatch(anyList())).thenReturn(3);

        // Act
        Map<String, Object> result = employeeService.batchUpdateEmployeeSalaryById(eids, sid);

        // Assert
        assertEquals("ok", result.get("status"));
        assertEquals(3, result.get("successCount"));
        assertTrue(((List<Integer>) result.get("notFoundEids")).isEmpty());

        // Verify change records were inserted
        verify(salaryChangeRecordMapper, times(1)).insertBatch(argThat(records -> {
            List<SalaryChangeRecord> list = (List<SalaryChangeRecord>) records;
            return list.size() == 3;
        }));
    }

    @Test
    void testBatchUpdatePartialEmployeeNotFound() {
        // Arrange
        Integer sid = 10;
        List<Integer> eids = Arrays.asList(1, 2, 999);

        // Mock salary exists
        Salary salary = new Salary();
        salary.setId(sid);
        org.javaboy.vhr.mapper.SalaryMapper salaryMapper = getSalaryMapper();
        when(salaryMapper.selectByPrimaryKey(sid)).thenReturn(salary);

        // Mock only employees 1 and 2 exist (999 not found)
        List<Employee> employees = new ArrayList<>();
        Employee emp1 = new Employee();
        emp1.setId(1);
        employees.add(emp1);
        Employee emp2 = new Employee();
        emp2.setId(2);
        employees.add(emp2);
        when(employeeMapper.getEmployeeByIds(eids)).thenReturn(employees);

        // Mock current bindings
        List<EmpSalary> currentBindings = new ArrayList<>();
        EmpSalary es1 = new EmpSalary();
        es1.setEid(1);
        es1.setSid(9);
        currentBindings.add(es1);
        when(employeeMapper.getEmpSalaryByEids(anyList())).thenReturn(currentBindings);

        // Mock update success
        when(employeeMapper.updateEmployeeSalaryById(eq(1), eq(sid))).thenReturn(2);
        when(employeeMapper.updateEmployeeSalaryById(eq(2), eq(sid))).thenReturn(1);

        // Mock change record insert
        when(salaryChangeRecordMapper.insertBatch(anyList())).thenReturn(2);

        // Act
        Map<String, Object> result = employeeService.batchUpdateEmployeeSalaryById(eids, sid);

        // Assert
        assertEquals("ok", result.get("status"));
        assertEquals(2, result.get("successCount"));
        List<Integer> notFoundEids = (List<Integer>) result.get("notFoundEids");
        assertEquals(1, notFoundEids.size());
        assertEquals(999, notFoundEids.get(0));
    }

    @Test
    void testBatchUpdateSalaryNotFound() {
        // Arrange
        Integer sid = 9999;
        List<Integer> eids = Arrays.asList(1, 2, 3);

        // Mock salary does not exist
        org.javaboy.vhr.mapper.SalaryMapper salaryMapper = getSalaryMapper();
        when(salaryMapper.selectByPrimaryKey(sid)).thenReturn(null);

        // Act
        Map<String, Object> result = employeeService.batchUpdateEmployeeSalaryById(eids, sid);

        // Assert
        assertEquals("error", result.get("status"));
        assertEquals("薪资账套不存在", result.get("msg"));

        // Verify no updates or inserts happened
        verify(employeeMapper, never()).updateEmployeeSalaryById(anyInt(), anyInt());
        verify(salaryChangeRecordMapper, never()).insertBatch(anyList());
    }

    @Test
    void testSingleUpdateRecordsChange() {
        // Arrange
        Integer eid = 1;
        Integer sid = 10;
        when(employeeMapper.updateEmployeeSalaryById(eid, sid)).thenReturn(2);
        when(salaryChangeRecordMapper.insertBatch(anyList())).thenReturn(1);

        // Act
        Integer result = employeeService.updateEmployeeSalaryById(eid, sid);

        // Assert
        assertEquals(Integer.valueOf(2), result);
        verify(salaryChangeRecordMapper, times(1)).insertBatch(argThat(records -> {
            List<SalaryChangeRecord> list = (List<SalaryChangeRecord>) records;
            return list.size() == 1
                    && list.get(0).getEid().equals(eid)
                    && list.get(0).getNewSid().equals(sid);
        }));
    }

    private org.javaboy.vhr.mapper.SalaryMapper getSalaryMapper() {
        try {
            java.lang.reflect.Field field = SalaryService.class.getDeclaredField("salaryMapper");
            field.setAccessible(true);
            return (org.javaboy.vhr.mapper.SalaryMapper) field.get(salaryService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
