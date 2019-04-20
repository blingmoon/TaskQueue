import com.blingsun.taskqueue.TaskBody;
import com.blingsun.taskqueue.TaskQueueByThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestMain {

    static private Logger logger = LoggerFactory.getLogger(TestMain.class);
    public static void main(String[] agr){

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,400,60, TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>());
        TaskQueueByThreadPool taskQueueByThreadPool = new TaskQueueByThreadPool(threadPoolExecutor);
        taskQueueByThreadPool.start();
        AtomicInteger count = new AtomicInteger(1);

        //启动开始线程
        ThreadPoolExecutor addExcutor = new ThreadPoolExecutor(
                4,4,60, TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>());
        for(int i=0;i<2;i++){
            addExcutor.execute(new AddTaskRunable(count,taskQueueByThreadPool));
        }
        int i = 0;
        while (true){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int id = (int) (Math.random()*count.get());
            long restTime = taskQueueByThreadPool.getRestTime(id);
            if(restTime !=0) logger.debug("任务{} 还剩{}s 触发",id,taskQueueByThreadPool.getRestTime(id));
            if(i++==0){
                taskQueueByThreadPool.registerTask("test", new TaskBody() {
                    @Override
                    public void execute() {
                        System.out.println("test 任务");
                    }
                },3600L,TimeUnit.SECONDS);
            }else {
                if((i&1) == 0){
                    logger.debug("任务{} 还剩{}s触发","test",taskQueueByThreadPool.getRestTime("test"));
                }
            }
        }
    }
}
