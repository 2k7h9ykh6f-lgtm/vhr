package org.javaboy.vhr.controller.salary;

import org.javaboy.vhr.model.Employee;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.model.RespPageBean;
import org.javaboy.vhr.model.Salary;
import org.javaboy.vhr.model.SalaryChangeLog;
import org.javaboy.vhr.service.EmployeeService;
import org.javaboy.vhr.service.SalaryService;
import org.javaboy.vhr.utils.HrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PutMapping("/batch")
    public RespBean batchUpdateEmployeeSalary(@RequestParam("eids") List<Integer> eids, @RequestParam("sid") Integer sid) {
        Hr currentHr = HrUtils.getCurrentHr();
        String operator = currentHr == null ? null : currentHr.getName();
        return employeeService.batchUpdateEmployeeSalary(eids, sid, operator);
    }

    @GetMapping("/log")
    public List<SalaryChangeLog> getSalaryChangeLogs(@RequestParam(required = false) Integer eid) {
        return salaryService.getSalaryChangeLogs(eid);
    }
}
