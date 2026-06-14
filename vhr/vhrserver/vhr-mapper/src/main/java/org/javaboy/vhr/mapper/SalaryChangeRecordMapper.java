package org.javaboy.vhr.mapper;

import org.apache.ibatis.annotations.Param;
import org.javaboy.vhr.model.SalaryChangeRecord;

import java.util.List;

public interface SalaryChangeRecordMapper {

    int insertBatch(@Param("list") List<SalaryChangeRecord> list);
}
