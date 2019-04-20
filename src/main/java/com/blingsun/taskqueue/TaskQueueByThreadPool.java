package com.blingsun.taskqueue;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * description:
 *  主要是线程池的持久化任务执行
 * @author zhouliang 297977761@qq.com
 * @version  1.0 2019/4/20  by zhouliang 297977761@qq.com 创建
 */
public class TaskQueueByThreadPool extends AbstractTaskQueue {
    private final Executor executor;

    public TaskQueueByThreadPool(Executor executor,Integer slotsLength, Integer timeAccuracy, TimeUnit timeUnit) {
        super(slotsLength, timeAccuracy, timeUnit);
        this.executor = executor;
    }
    public TaskQueueByThreadPool(Executor executor) {
        this.executor = executor;
    }



    @Override
    protected void excuteTask(final TaskBody taskBody) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                taskBody.execute();
            }
        });
    }
}
