package org.javaboy.vhr.mapper;

import org.apache.ibatis.annotations.Param;
import org.javaboy.vhr.model.SalaryChangeLog;

import java.util.List;

public interface SalaryChangeLogMapper {
    int insert(SalaryChangeLog record);

    List<SalaryChangeLog> getSalaryChangeLogsByEid(@Param("eid") Integer eid);
}
