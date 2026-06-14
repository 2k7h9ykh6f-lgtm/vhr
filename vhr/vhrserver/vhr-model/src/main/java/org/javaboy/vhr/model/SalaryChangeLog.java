package org.javaboy.vhr.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 薪资账套变更记录：记录某员工薪资账套从 oldSid 变更为 newSid 的操作人与时间。
 */
public class SalaryChangeLog {
    private Integer id;

    private Integer eid;

    private Integer oldSid;

    private Integer newSid;

    private String operator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getEid() {
        return eid;
    }

    public void setEid(Integer eid) {
        this.eid = eid;
    }

    public Integer getOldSid() {
        return oldSid;
    }

    public void setOldSid(Integer oldSid) {
        this.oldSid = oldSid;
    }

    public Integer getNewSid() {
        return newSid;
    }

    public void setNewSid(Integer newSid) {
        this.newSid = newSid;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
