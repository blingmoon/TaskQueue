package com.blingsun.taskqueue;

import com.blingsun.taskqueue.taskpresist.AofTask;
import org.apache.commons.beanutils.BeanMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * description:
 * 这个可以使用代理的设计方式来进行处理,会比较好，静态代理
 * @author zhouliang 297977761@qq.com
 * @version 1.0 2019/4/21  by zhouliang 297977761@qq.com 创建
 */
public class TaskQueueAOF extends TaskQueueByThreadPool {

    static private final Logger logger = LoggerFactory.getLogger(TaskQueueAOF.class);

    private final String queueAofKey;
    private ObjectOutputStream os;

    public TaskQueueAOF(String queueAofKey,Executor executor, Integer slotsLength, Integer timeAccuracy, TimeUnit timeUnit) {
        super(executor, slotsLength, timeAccuracy, timeUnit);
        this.queueAofKey = queueAofKey;
    }


    public TaskQueueAOF(String queueAofKey,Executor executor) {
        super(executor);
        this.queueAofKey = queueAofKey;
    }

    /**
     * 这个是开始的时候加载动作,
     * 如果持久化了的话,在启动的时候需要去重写这个函数
     */
    private  void loadHistory(){

    }

    /**
     * 用来实现持久化任务,重写这个方法可以进行持久化的方式选择
     * @param taskId 任务的id
     * @param taskBody 任务的执行题
     * @param time 定时的时间
     * @param timeUnit 定时的时间单位
     * @return 是否成功持久化，成功为TRUE,失败返回False
     */
    private Boolean persistenceTask(Object taskId,TaskBody taskBody,Long time,TimeUnit timeUnit){
        //需要持久化的的内容
        /*
        * 1.任务的id
        * 2.taskBody接口,主要是这个结构可能有参数
        * 3.taskBody接口的具体class
        * 4.剩余时间 ---- 统统转化为millis
        * 5.当前的注册时间
        * 6.op是删除还是修改
        * */

        //1.先检查taskBody有没有实现Serializable,没有话直接pass
        if(!(taskBody instanceof Serializable)){
            logger.debug("task{} 任务是不可以持久化,请检查class{} 是不是实现了Serializable接口",taskId,taskBody.getClass());
            return false;
        }

        //2.持久化的class
        AofTask aofTask = new AofTask(1,taskId.toString(),System.currentTimeMillis(),timeUnit.toMillis(time),taskBody.getClass(),taskBody);

        return writeAof(aofTask);
    }

    private Boolean writeAof(AofTask aofTask){
        Boolean result = true;
        //3.AofTask
        try {
            ObjectOutputStream os = getObjectOutStream();
            if(os !=null){
                os.writeObject(aofTask);
            }
        } catch (IOException e) {
            result = false;
            logger.debug("获取aof文件流失败请检查\n",e);
        }finally {
            try {
                os.flush();
            } catch (IOException e) {
                result = false;
                logger.debug("输入流刷新异常\n",e);
            }
        }
        return  result;
    }

    private ObjectOutputStream getObjectOutStream() throws IOException {
        if(os != null) return os;

        File file = new File("aof"+File.separator+this.queueAofKey+".bat");
        if(file.exists()){
            //文件存在
            FileOutputStream fo = new FileOutputStream(file,true);
            os = new ObjectOutputStream(fo);
            long pos = 0;
            fo.getChannel().truncate(pos);
        }else {
            if(!file.createNewFile()){
                logger.debug("aof文件{}创建失败,检查失败原因",file.getName());
                return null;
            }
            FileOutputStream fo = new FileOutputStream(file,true);
            os = new ObjectOutputStream(fo);
        }
        return os;
    }
    /**
     * 用来实现删除持久化任务,在remove使用到
     * @param taskId 任务的id
     * @return 是否成功删除，成功为TRUE,失败返回False
     */
    private  Boolean deleteTask(Object taskId){
        AofTask aofTask = new AofTask(2,taskId.toString());
        return writeAof(aofTask);
    }

    @Override
    public void registerTask(Object taskId, TaskBody taskBody, Long time, TimeUnit timeUnit) {
        //持久化任务选择
        if(!persistenceTask(taskId,taskBody,time,timeUnit)){
            //没有成功持久化
            if(logger.isDebugEnabled()){
                logger.debug("任务 taskId {},taskBody {},没有成功持久化",taskId,taskBody);
            }
        }
        super.registerTask(taskId, taskBody, time, timeUnit);
    }

    @Override
    public void removeTask(Object key) {
        //删除任务持久化任务
        if(deleteTask(key)&&logger.isDebugEnabled()){
            logger.debug("任务{}已经被除移",key);
        }
        super.removeTask(key);
    }

    @Override
    public void run() {
        //静态代理,这个需要去加载loadHistory
        loadHistory();
        super.run();
    }
}
