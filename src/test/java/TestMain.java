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
        for(int i=0;i<8;i++){
            addExcutor.execute(new AddTaskRunable(count,taskQueueByThreadPool));
        }
        while (true){
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
