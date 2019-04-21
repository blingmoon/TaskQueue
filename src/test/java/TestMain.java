import com.blingsun.taskqueue.TaskBody;
import com.blingsun.taskqueue.TaskQueueAOF;
import com.blingsun.taskqueue.TaskQueueByThreadPool;
import com.blingsun.taskqueue.taskpresist.AofTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestMain {

    static private Logger logger = LoggerFactory.getLogger(TestMain.class);

    private static void test(Runnable runnable){
        System.out.println(runnable.getClass());
    }

    /**
     * 常规的测试
     */
    private static void testOnce(){

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,400,60, TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>());
        TaskQueueByThreadPool taskQueueByThreadPool = new TaskQueueByThreadPool(threadPoolExecutor);
        taskQueueByThreadPool.start();
        AtomicInteger count = new AtomicInteger(1);

        //启动开始线程
        ThreadPoolExecutor addExcutor = new ThreadPoolExecutor(
                4,4,60, TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>());

        AddTaskRunable addTaskRunable = new AddTaskRunable(count,taskQueueByThreadPool);


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

    /**
     * 持久化相关的测试
     */
    private static void testTwoWrite(){
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            5,400,60, TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>());

        TaskQueueAOF taskQueueAOF = new TaskQueueAOF("testTwo",threadPoolExecutor);

        taskQueueAOF.registerTask("one",new AofTaskBody("one"),200L,TimeUnit.SECONDS);

        taskQueueAOF.registerTask("two",new AofTaskBody("two"),200L,TimeUnit.SECONDS);

        taskQueueAOF.removeTask("one");
    }

    private static void testTwoRead(){
        File file = new File("aof"+File.separator+"testTwo.bat");
        ArrayList<AofTask> list = new ArrayList<>();

        if (file.exists()){
            ObjectInputStream ois;
            try {
                FileInputStream fn = new FileInputStream(file);
                ois = new ObjectInputStream(fn);
                while (fn.available()>0){
                    //文件还有数据
                    AofTask  aofTask = (AofTask) ois.readObject();
                    list.add(aofTask);
                }
                ois.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        System.out.println("---end---");

    }
    public static void main(String[] agr){
        testTwoRead();
    }
}
