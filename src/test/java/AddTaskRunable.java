import com.blingsun.taskqueue.TaskBody;
import com.blingsun.taskqueue.TaskQueueByThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.rmi.runtime.Log;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * description:
 *
 * @author zhouliang 297977761@qq.com
 * @version 1.0 2019/4/20  by zhouliang 297977761@qq.com 创建
 */
public class AddTaskRunable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(AddTaskRunable.class);
    private static final int MAX_COUNT = 5;
    private final AtomicInteger atomicInteger;
    private final TaskQueueByThreadPool taskQueueByThreadPool;

    AddTaskRunable(AtomicInteger atomicInteger,TaskQueueByThreadPool taskQueueByThreadPool) {
        this.atomicInteger = atomicInteger;
        this.taskQueueByThreadPool = taskQueueByThreadPool;
    }

    @Override
    public void run() {
        int taskcount = 0;
        while (taskcount++<MAX_COUNT){
            int count = atomicInteger.getAndIncrement();
            //System.out.println(Thread.currentThread().getName() + "----" + count);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long nextTime =(long)(Math.random()*100);

            taskQueueByThreadPool.registerTask(count,new TestTask(count),nextTime,TimeUnit.SECONDS);
        }
    }

    private class TestTask implements TaskBody{
        private  final  int taskId;

        private TestTask(int taskId) {
            this.taskId = taskId;
        }

        @Override
        public void execute() {
            try {
                //这边是用线程池的管理去执行,所以这个对任务队列对走动是没有太大影响的
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.debug("任务{}已经触发",taskId);
        }
    }
}
