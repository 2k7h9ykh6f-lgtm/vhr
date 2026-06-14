package org.javaboy.vhr.controller.salary;

import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.model.RespPageBean;
import org.javaboy.vhr.model.Salary;
import org.javaboy.vhr.service.EmployeeService;
import org.javaboy.vhr.service.SalaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/salary/sobcfg")
public class SobConfigController {
    @Autowired
    EmployeeService employeeService;
    @Autowired
    SalaryService salaryService;

    @GetMapping("/")
    public RespPageBean getEmployeeByPageWithSalary(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size) {
        return employeeService.getEmployeeByPageWithSalary(page, size);
    }

    @GetMapping("/salaries")
    public List<Salary> getAllSalaries() {
        return salaryService.getAllSalaries();
    }

    @PutMapping("/")
    public RespBean updateEmployeeSalaryById(Integer eid, Integer sid) {
        Integer result = employeeService.updateEmployeeSalaryById(eid, sid);
        if (result == 1 || result == 2) {
            return RespBean.ok("更新成功");
        }
        return RespBean.error("更新失败");
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/batch")
    public RespBean batchUpdateEmployeeSalaryById(@RequestBody Map<String, Object> params) {
        List<Integer> eids = (List<Integer>) params.get("eids");
        Integer sid = (Integer) params.get("sid");
        if (eids == null || eids.isEmpty() || sid == null) {
            return RespBean.error("参数不完整，需要提供 eids 和 sid");
        }
        Map<String, Object> result = employeeService.batchUpdateEmployeeSalaryById(eids, sid);
        if ("error".equals(result.get("status"))) {
            return RespBean.error((String) result.get("msg"));
        }
        Integer successCount = (Integer) result.get("successCount");
        @SuppressWarnings("unchecked")
        List<Integer> notFoundEids = (List<Integer>) result.get("notFoundEids");
        if (notFoundEids != null && !notFoundEids.isEmpty()) {
            String msg = String.format("成功更新%d名员工，%d名员工不存在", successCount, notFoundEids.size());
            return RespBean.ok(msg, notFoundEids);
        }
        return RespBean.ok(String.format("成功批量更新%d名员工薪资账套", successCount));
    }
}
