package com.blingsun.taskqueue;

/**
 * description:
 * 任务的执行体,需要自己实现任务的执行体
 * @author BlingSun liang.zhou01@ucarinc.com
 * @version 2018/9/30 14:32
 * @since 2018/9/30 14:32
 **/
public interface TaskBody {

    /**
     * 本身不支持传参，需要传入参数的话
     * 请自己在接口的实现中添加属性
     * 任务的执行函数
     */
    void execute();
}

