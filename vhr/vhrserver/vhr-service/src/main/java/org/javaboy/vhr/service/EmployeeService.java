package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.EmployeeMapper;
import org.javaboy.vhr.mapper.SalaryChangeRecordMapper;
import org.javaboy.vhr.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @作者 江南一点雨
 * @公众号 江南一点雨
 * @微信号 a_java_boy
 * @GitHub https://github.com/lenve
 * @博客 http://wangsong.blog.csdn.net
 * @网站 http://www.javaboy.org
 * @时间 2019-10-29 7:44
 */
@Service
public class EmployeeService {
    @Autowired
    EmployeeMapper employeeMapper;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    MailSendLogService mailSendLogService;
    @Autowired
    SalaryChangeRecordMapper salaryChangeRecordMapper;
    @Autowired
    SalaryService salaryService;
    public final static Logger logger = LoggerFactory.getLogger(EmployeeService.class);
    SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
    SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
    DecimalFormat decimalFormat = new DecimalFormat("##.00");

    public RespPageBean getEmployeeByPage(Integer page, Integer size, Employee employee, Date[] beginDateScope) {
        if (page != null && size != null) {
            page = (page - 1) * size;
        }
        List<Employee> data = employeeMapper.getEmployeeByPage(page, size, employee, beginDateScope);
        Long total = employeeMapper.getTotal(employee, beginDateScope);
        RespPageBean bean = new RespPageBean();
        bean.setData(data);
        bean.setTotal(total);
        return bean;
    }

    public Integer addEmp(Employee employee) {
        Date beginContract = employee.getBeginContract();
        Date endContract = employee.getEndContract();
        double month = (Double.parseDouble(yearFormat.format(endContract)) - Double.parseDouble(yearFormat.format(beginContract))) * 12 + (Double.parseDouble(monthFormat.format(endContract)) - Double.parseDouble(monthFormat.format(beginContract)));
        employee.setContractTerm(Double.parseDouble(decimalFormat.format(month / 12)));
        int result = employeeMapper.insertSelective(employee);
        if (result == 1) {
            Employee emp = employeeMapper.getEmployeeById(employee.getId());
            //生成消息的唯一id
            String msgId = UUID.randomUUID().toString();
            MailSendLog mailSendLog = new MailSendLog();
            mailSendLog.setMsgId(msgId);
            mailSendLog.setCreateTime(new Date());
            mailSendLog.setExchange(MailConstants.MAIL_EXCHANGE_NAME);
            mailSendLog.setRouteKey(MailConstants.MAIL_ROUTING_KEY_NAME);
            mailSendLog.setEmpId(emp.getId());
            mailSendLog.setTryTime(new Date(System.currentTimeMillis() + 1000 * 60 * MailConstants.MSG_TIMEOUT));
            mailSendLogService.insert(mailSendLog);
            rabbitTemplate.convertAndSend(MailConstants.MAIL_EXCHANGE_NAME, MailConstants.MAIL_ROUTING_KEY_NAME, emp, new CorrelationData(msgId));
        }
        return result;
    }

    public Integer maxWorkID() {
        return employeeMapper.maxWorkID();
    }

    public Integer deleteEmpByEid(Integer id) {
        return employeeMapper.deleteByPrimaryKey(id);
    }

    public Integer updateEmp(Employee employee) {
        return employeeMapper.updateByPrimaryKeySelective(employee);
    }

    public Integer addEmps(List<Employee> list) {
        return employeeMapper.addEmps(list);
    }

    public RespPageBean getEmployeeByPageWithSalary(Integer page, Integer size) {
        if (page != null && size != null) {
            page = (page - 1) * size;
        }
        List<Employee> list = employeeMapper.getEmployeeByPageWithSalary(page, size);
        RespPageBean respPageBean = new RespPageBean();
        respPageBean.setData(list);
        respPageBean.setTotal(employeeMapper.getTotal(null, null));
        return respPageBean;
    }

    public Integer updateEmployeeSalaryById(Integer eid, Integer sid) {
        Integer result = employeeMapper.updateEmployeeSalaryById(eid, sid);
        if (result == 1 || result == 2) {
            recordSalaryChange(eid, null, sid);
        }
        return result;
    }

    public Employee getEmployeeById(Integer empId) {
        return employeeMapper.getEmployeeById(empId);
    }

    public Map<String, Object> batchUpdateEmployeeSalaryById(List<Integer> eids, Integer sid) {
        Map<String, Object> resultMap = new HashMap<>();

        // 校验账套是否存在
        Salary salary = salaryService.getSalaryById(sid);
        if (salary == null) {
            resultMap.put("status", "error");
            resultMap.put("msg", "薪资账套不存在");
            return resultMap;
        }

        // 查询存在的员工
        List<Employee> existingEmployees = employeeMapper.getEmployeeByIds(eids);
        Set<Integer> existingEids = existingEmployees.stream()
                .map(Employee::getId)
                .collect(Collectors.toSet());

        // 找出不存在的员工
        List<Integer> notFoundEids = eids.stream()
                .filter(eid -> !existingEids.contains(eid))
                .collect(Collectors.toList());

        // 查询当前员工的旧账套绑定
        List<EmpSalary> currentBindings = existingEids.isEmpty()
                ? Collections.emptyList()
                : employeeMapper.getEmpSalaryByEids(new ArrayList<>(existingEids));
        Map<Integer, Integer> oldSidMap = new HashMap<>();
        for (EmpSalary es : currentBindings) {
            oldSidMap.put(es.getEid(), es.getSid());
        }

        // 逐个更新存在的员工账套
        int successCount = 0;
        Date now = new Date();
        Hr operator = getCurrentOperator();
        List<SalaryChangeRecord> records = new ArrayList<>();

        for (Integer eid : existingEids) {
            Integer updateResult = employeeMapper.updateEmployeeSalaryById(eid, sid);
            if (updateResult == 1 || updateResult == 2) {
                successCount++;
                SalaryChangeRecord record = new SalaryChangeRecord();
                record.setEid(eid);
                record.setOldSid(oldSidMap.get(eid));
                record.setNewSid(sid);
                record.setOperateTime(now);
                if (operator != null) {
                    record.setOperatorId(operator.getId());
                    record.setOperatorName(operator.getName());
                }
                records.add(record);
            }
        }

        // 批量插入变更记录
        if (!records.isEmpty()) {
            salaryChangeRecordMapper.insertBatch(records);
        }

        resultMap.put("status", "ok");
        resultMap.put("successCount", successCount);
        resultMap.put("notFoundEids", notFoundEids);
        return resultMap;
    }

    private void recordSalaryChange(Integer eid, Integer oldSid, Integer newSid) {
        SalaryChangeRecord record = new SalaryChangeRecord();
        record.setEid(eid);
        record.setOldSid(oldSid);
        record.setNewSid(newSid);
        record.setOperateTime(new Date());
        Hr operator = getCurrentOperator();
        if (operator != null) {
            record.setOperatorId(operator.getId());
            record.setOperatorName(operator.getName());
        }
        salaryChangeRecordMapper.insertBatch(Collections.singletonList(record));
    }

    private Hr getCurrentOperator() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Hr) {
                return (Hr) authentication.getPrincipal();
            }
        } catch (Exception e) {
            logger.warn("获取当前操作人失败", e);
        }
        return null;
    }
}
