package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.EmployeeMapper;
import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.RespPageBean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 纯单元测试：不加载 Spring 上下文、不连数据库，只用 Mockito 验证 EmployeeService 对
 * 员工组合查询的编排逻辑（分页偏移、新增筛选条件透传、结果透传、positionId/posId 桥接）。
 *
 * SQL 层面的过滤与 contractRemainingDays（DATEDIFF）计算依赖 MySQL，见任务说明中的
 * “验证（Verification）”部分（SQL 直验 + 接口手测）。
 */
public class EmployeeServiceTest {

    private static Date date(String yyyyMMdd) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(yyyyMMdd);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
    }

    private EmployeeService newServiceWith(EmployeeMapper mapper) {
        EmployeeService service = new EmployeeService();
        // employeeMapper 为包级私有字段，本测试与 EmployeeService 同包，可直接注入 mock。
        // 该方法不触及 rabbitTemplate / mailSendLogService，故无需赋值。
        service.employeeMapper = mapper;
        return service;
    }

    @Test
    public void getEmployeeByPage_appliesPaginationOffset_andForwardsAllFilters() {
        EmployeeMapper mapper = Mockito.mock(EmployeeMapper.class);
        EmployeeService service = newServiceWith(mapper);

        Employee filter = new Employee();
        filter.setDepartmentId(1);
        filter.setPositionId(5); // 经 setPositionId 桥接到 posId
        filter.setJobLevelId(9);
        filter.setBeginContractEndDate(date("2025-01-01"));
        filter.setEndContractEndDate(date("2025-12-31"));
        filter.setContractExpireWithinDays(30);
        Date[] scope = new Date[]{date("2018-01-01"), date("2018-12-31")};

        Employee row = new Employee();
        row.setId(1);
        row.setContractRemainingDays(15); // 模拟 mapper 由 SQL 计算回填
        List<Employee> data = Collections.singletonList(row);
        Mockito.when(mapper.getEmployeeByPage(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(data);
        Mockito.when(mapper.getTotal(Mockito.any(), Mockito.any())).thenReturn(1L);

        RespPageBean bean = service.getEmployeeByPage(2, 10, filter, scope);

        // 分页偏移：page 应被转换为 (2-1)*10 = 10，size 不变。
        ArgumentCaptor<Integer> pageCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Employee> empCap = ArgumentCaptor.forClass(Employee.class);
        ArgumentCaptor<Date[]> scopeCap = ArgumentCaptor.forClass(Date[].class);
        Mockito.verify(mapper).getEmployeeByPage(pageCap.capture(), sizeCap.capture(), empCap.capture(), scopeCap.capture());
        assertEquals(10, (int) pageCap.getValue());
        assertEquals(10, (int) sizeCap.getValue());

        // 新增筛选条件全部原样透传到 getEmployeeByPage。
        Employee passed = empCap.getValue();
        assertEquals(5, (int) passed.getPosId());            // positionId -> posId
        assertEquals(1, (int) passed.getDepartmentId());
        assertEquals(9, (int) passed.getJobLevelId());
        assertEquals(date("2025-01-01"), passed.getBeginContractEndDate());
        assertEquals(date("2025-12-31"), passed.getEndContractEndDate());
        assertEquals(30, (int) passed.getContractExpireWithinDays());
        assertSame(scope, scopeCap.getValue());

        // getTotal 必须收到同样的筛选条件与日期范围，保证总数与过滤后数据一致。
        ArgumentCaptor<Employee> empCapTotal = ArgumentCaptor.forClass(Employee.class);
        ArgumentCaptor<Date[]> scopeCapTotal = ArgumentCaptor.forClass(Date[].class);
        Mockito.verify(mapper).getTotal(empCapTotal.capture(), scopeCapTotal.capture());
        assertEquals(30, (int) empCapTotal.getValue().getContractExpireWithinDays());
        assertEquals(date("2025-01-01"), empCapTotal.getValue().getBeginContractEndDate());
        assertSame(scope, scopeCapTotal.getValue());

        // 结果透传：data 与 total 原样返回，contractRemainingDays 随结果透出。
        assertEquals(1L, bean.getTotal());
        assertSame(data, bean.getData());
        @SuppressWarnings("unchecked")
        List<Employee> returned = (List<Employee>) bean.getData();
        assertEquals(15, (int) returned.get(0).getContractRemainingDays());
    }

    @Test
    public void getEmployeeByPage_nullPageAndSize_doesNotComputeOffset() {
        EmployeeMapper mapper = Mockito.mock(EmployeeMapper.class);
        EmployeeService service = newServiceWith(mapper);

        Mockito.when(mapper.getEmployeeByPage(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Collections.emptyList());
        Mockito.when(mapper.getTotal(Mockito.any(), Mockito.any())).thenReturn(0L);

        service.getEmployeeByPage(null, null, new Employee(), null);

        ArgumentCaptor<Integer> pageCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCap = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(mapper).getEmployeeByPage(pageCap.capture(), sizeCap.capture(), Mockito.any(), Mockito.any());
        assertNull(pageCap.getValue());
        assertNull(sizeCap.getValue());
    }

    @Test
    public void positionId_isAliasOfPosId() {
        Employee e = new Employee();
        e.setPositionId(7);
        assertEquals(7, (int) e.getPosId());

        e.setPosId(3);
        assertEquals(3, (int) e.getPositionId());
    }
}
