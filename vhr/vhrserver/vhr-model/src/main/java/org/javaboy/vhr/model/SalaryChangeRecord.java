package org.javaboy.vhr.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class SalaryChangeRecord {
    private Integer id;

    private Integer eid;

    private Integer oldSid;

    private Integer newSid;

    private Integer operatorId;

    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date operateTime;

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

    public Integer getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Integer operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName == null ? null : operatorName.trim();
    }

    public Date getOperateTime() {
        return operateTime;
    }

    public void setOperateTime(Date operateTime) {
        this.operateTime = operateTime;
    }
}
