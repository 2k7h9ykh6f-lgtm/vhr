package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.EmployeeMapper;
import org.javaboy.vhr.mapper.SalaryChangeLogMapper;
import org.javaboy.vhr.mapper.SalaryMapper;
import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.model.MailConstants;
import org.javaboy.vhr.model.MailSendLog;
import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.model.RespPageBean;
import org.javaboy.vhr.model.SalaryChangeLog;
import org.javaboy.vhr.utils.HrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    SalaryMapper salaryMapper;
    @Autowired
    SalaryChangeLogMapper salaryChangeLogMapper;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    MailSendLogService mailSendLogService;
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
        Integer oldSid = employeeMapper.getSalaryIdByEid(eid);
        Integer result = employeeMapper.updateEmployeeSalaryById(eid, sid);
        if (result != null && result > 0) {
            recordSalaryChange(eid, oldSid, sid, currentOperatorName());
        }
        return result;
    }

    /**
     * 批量设置员工薪资账套（尽力更新：更新存在的员工，跳过并报告不存在的员工）。
     *
     * @param eids     员工编号列表
     * @param sid      目标薪资账套编号
     * @param operator 操作人（由控制器从登录用户解析后传入，便于单元测试）
     * @return obj 中包含 successCount（成功条数）与 notFound（不存在的 eid 列表）
     */
    @Transactional
    public RespBean batchUpdateEmployeeSalary(List<Integer> eids, Integer sid, String operator) {
        if (sid == null || salaryMapper.selectByPrimaryKey(sid) == null) {
            return RespBean.error("薪资账套不存在");
        }
        if (eids == null || eids.isEmpty()) {
            return RespBean.error("请选择要更新薪资账套的员工");
        }
        List<Integer> notFound = new ArrayList<>();
        int successCount = 0;
        for (Integer eid : eids) {
            if (eid == null || employeeMapper.selectByPrimaryKey(eid) == null) {
                notFound.add(eid);
                continue;
            }
            Integer oldSid = employeeMapper.getSalaryIdByEid(eid);
            employeeMapper.updateEmployeeSalaryById(eid, sid);
            recordSalaryChange(eid, oldSid, sid, operator);
            successCount++;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("successCount", successCount);
        data.put("notFound", notFound);
        if (successCount == 0) {
            return RespBean.error("更新失败，没有有效的员工", data);
        }
        if (!notFound.isEmpty()) {
            return RespBean.ok("部分更新成功，部分员工不存在", data);
        }
        return RespBean.ok("批量更新成功", data);
    }

    private void recordSalaryChange(Integer eid, Integer oldSid, Integer newSid, String operator) {
        SalaryChangeLog changeLog = new SalaryChangeLog();
        changeLog.setEid(eid);
        changeLog.setOldSid(oldSid);
        changeLog.setNewSid(newSid);
        changeLog.setOperator(operator);
        changeLog.setCreateTime(new Date());
        salaryChangeLogMapper.insert(changeLog);
    }

    private String currentOperatorName() {
        try {
            Hr hr = HrUtils.getCurrentHr();
            return hr == null ? null : hr.getName();
        } catch (Exception e) {
            return null;
        }
    }

    public Employee getEmployeeById(Integer empId) {
        return employeeMapper.getEmployeeById(empId);
    }
}
