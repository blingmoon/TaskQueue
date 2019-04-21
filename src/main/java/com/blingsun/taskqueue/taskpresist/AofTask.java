package com.blingsun.taskqueue.taskpresist;

import java.io.Serializable;
import java.util.Map;

/**
 * description:
 * 日志文件的对象
 * op,key,createTime,restTime,taskBodyClassName,taskBodyValue;
 * @author zhouliang 297977761@qq.com
 * @version 1.0 2019/4/21  by zhouliang 297977761@qq.com 创建
 */
public class AofTask implements Serializable {
    //op = 1 为添加,op = 2为删除
    private Integer op;
    private String taskKey;
    private long currentTime;
    private long restTime;
    private Class taskBodyClass;
    private Object taskBody;

    public AofTask(Integer op, String taskKey, long currentTime, long restTime, Class taskBodyClass, Object taskBody) {
        this.op = op;
        this.taskKey = taskKey;
        this.currentTime = currentTime;
        this.restTime = restTime;
        this.taskBodyClass = taskBodyClass;
        this.taskBody = taskBody;
    }

    public AofTask(Integer op, String taskKey) {
        this.op = op;
        this.taskKey = taskKey;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public Integer getOp() {
        return op;
    }

    public void setOp(Integer op) {
        this.op = op;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }

    public long getRestTime() {
        return restTime;
    }

    public void setRestTime(long restTime) {
        this.restTime = restTime;
    }

    public Class getTaskBodyClass() {
        return taskBodyClass;
    }

    public void setTaskBodyClass(Class taskBodyClass) {
        this.taskBodyClass = taskBodyClass;
    }

    public Object getTaskBodyMap() {
        return taskBody;
    }

    public void setTaskBodyMap(Map taskBodyMap) {
        this.taskBody = taskBodyMap;
    }
}
