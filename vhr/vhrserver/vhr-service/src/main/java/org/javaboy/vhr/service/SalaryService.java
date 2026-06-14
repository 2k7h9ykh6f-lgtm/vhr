package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.SalaryChangeLogMapper;
import org.javaboy.vhr.mapper.SalaryMapper;
import org.javaboy.vhr.model.Salary;
import org.javaboy.vhr.model.SalaryChangeLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SalaryService {
    @Autowired
    SalaryMapper salaryMapper;
    @Autowired
    SalaryChangeLogMapper salaryChangeLogMapper;

    public List<Salary> getAllSalaries() {
        return salaryMapper.getAllSalaries();
    }

    public Integer addSalary(Salary salary) {
        salary.setCreateDate(new Date());
        return salaryMapper.insertSelective(salary);
    }

    public Integer deleteSalaryById(Integer id) {
        return salaryMapper.deleteByPrimaryKey(id);
    }

    public Integer updateSalaryById(Salary salary) {
        return salaryMapper.updateByPrimaryKeySelective(salary);
    }

    public List<SalaryChangeLog> getSalaryChangeLogs(Integer eid) {
        return salaryChangeLogMapper.getSalaryChangeLogsByEid(eid);
    }
}
