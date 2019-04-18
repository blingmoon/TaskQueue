package com.blingsun.taskqueue.taskpresist;


import org.apache.commons.beanutils.BeanMap;

import java.io.Serializable;
import java.util.Map;

/**
 * description:
 *  定时任务的注册参数
 * @author BlingSun liang.zhou01@ucarinc.com
 * @version 1.0 2018/12/12 16:11 by BlingSun (liang.zhou01@ucarinc.com) 创建
 **/
public class TaskRegisterParam implements Serializable {
    /**
     * 定时任务的id
     */
    private String taskKey;

    /**
     * 执行体的参数
     */
    private  Map taskBodyParams;

    /**
     * 任务的class名字在TaskClassPersistence的key值
     */
    private String nameKey;

    /**
     * 实例的创建时间
     */
    private Long currentTime;



    /**
     * 多长时间之后执行
     */
    private Integer restTime;

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public Map getTaskBodyParams() {
        return taskBodyParams;
    }

    public void setTaskBodyParams(BeanMap taskBodyParams) {
        this.taskBodyParams = taskBodyParams;
    }

    public String getNameKey() {
        return nameKey;
    }

    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    public Long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(Long currentTime) {
        this.currentTime = currentTime;
    }

    public Integer getRestTime() {
        return restTime;
    }

    public void setRestTime(Integer restTime) {
        this.restTime = restTime;
    }

}

