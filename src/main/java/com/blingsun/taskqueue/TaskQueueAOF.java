package com.blingsun.taskqueue;

import com.blingsun.taskqueue.taskpresist.AofTask;
import org.apache.commons.beanutils.BeanMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * description:
 * 这个可以使用代理的设计方式来进行处理,会比较好，静态代理
 * @author zhouliang 297977761@qq.com
 * @version 1.0 2019/4/21  by zhouliang 297977761@qq.com 创建
 */
public class TaskQueueAOF extends TaskQueueByThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(TaskQueueAOF.class);
    private static final String CONFIG_NAME = "taskqueueconfig.properties";
    /**
     * FILE_LOCK主要是为了同步配置文件,多个实例共享,现在暂时控制的是taskqueueconfig.properties访问
     */
    private static final Object FILE_LOCK = new Object();
    private final String queueAofKey;
    private Long vesion=null;
    private ObjectOutputStream os;

    /**
     * 加载version文件
     */
    private static Properties propertiesLoad(){
        Properties properties = new Properties();

        synchronized (FILE_LOCK){
            File file =new File("config"+File.separator+CONFIG_NAME);
            InputStream in = null;
            try {
                if(!file.exists() && !file.createNewFile()){
                    logger.debug("文件{}不存在,同时也创建失败",file.getName());
                    return properties;
                }
                in= new BufferedInputStream(new FileInputStream(file));
                properties.load(in);
            } catch (IOException e) {
                if(logger.isDebugEnabled()){
                    logger.debug("taskqueueconfig.properties load failed",e);
                }
            }finally {
                try {
                    if(in!=null){
                        in.close();
                    }
                } catch (IOException e) {
                    if(logger.isDebugEnabled()){
                        logger.debug("taskqueueconfig.properties file close failed",e);
                    }
                }
            }
        }
        return properties;
    }

    private  void refreshConfig(Map<String,String> update){
        synchronized (FILE_LOCK){
            //加载原文件,应为有这个FILE_LOCK的关系，相当于事务的控制了
            Properties properties = propertiesLoad();
            for (String key:update.keySet()){
                properties.setProperty(key,update.get(key));
            }
            //写回配置文件
            OutputStreamWriter outputStreamWriter ;
            try {
                File file = new File("config"+File.separator+CONFIG_NAME);
                if(!file.exists() && !file.createNewFile()){
                    logger.debug("文件{}不存在,并且创建失败了,请查看原因",file.getName());
                }
                outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file,false));
                properties.store(outputStreamWriter,""+System.currentTimeMillis());
            } catch (IOException e) {
                logger.debug("文件操作异常",e);
            }
        }
    }


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
        //加载历史文件
        File file = new File("aof"+File.separator+this.queueAofKey+this.loadVersion()+".bat");
        LinkedList<AofTask> list = new LinkedList<>();
        if (file.exists()){
            ObjectInputStream ois =null;
            try {
                FileInputStream fn = new FileInputStream(file);
                ois = new ObjectInputStream(fn);
                while (fn.available()>0){
                    //文件还有数据
                    AofTask  aofTask = (AofTask) ois.readObject();
                    //后面需要逆序遍历,所以采用头插法
                    list.addFirst(aofTask);
                }
            } catch (IOException e) {
                logger.debug("文件打开异常,请查看文件{}",file.getName(),e);
            } catch (ClassNotFoundException e) {
                logger.debug("任务class加载失败",e);
            }finally {
                if(ois!=null){
                    try {
                        ois.close();
                    } catch (IOException e) {
                        logger.debug("文件关闭失败",e);
                    }
                }
            }
        }

        //出于效率的考虑用该是由后向上进行遍历,这个前提是操作是按顺序完成,前面是倒叙插入的,所以直接顺序遍历list就行
        Set<String> deleteMap = new HashSet<>(64);
        long currentTime = System.currentTimeMillis();
        for(AofTask aofTask:list){
            //检查aofTask是不是在delete里面,在的话说明删除
            if(deleteMap.contains(aofTask.getTaskKey())){continue;}
            //不在deleteMap里面
            if(aofTask.getOp() == 1){
                //添加操作
                //看看这个任务是不是已经被添加了 1.add A  2.delte A 3.add A  1和3的A不一样，任务队列中应该是3.的A
                if(!this.taskPool.containsKey(aofTask.getTaskKey())){
                    //任务没有被添加,添加任务,这个添加动作不要去走持久化路线,因为这个任务已经被持久化了,所以调用的是父辈的注册，而不是子类的注册
                    //获得剩余时间,当前时间是不是达到的
                    long rest = (aofTask.getCurrentTime()+aofTask.getRestTime() >currentTime)?aofTask.getCurrentTime()+aofTask.getRestTime()-currentTime:0;
                    super.registerTask(aofTask.getTaskKey(),aofTask.getTaskBody(),rest,TimeUnit.MILLISECONDS);
                }
            }else {
                //是删除操作,同时不在删除操作map里面,所以需要自己在删除操作里面添加
                deleteMap.add(aofTask.getTaskKey());
            }
        }

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
        AofTask aofTask = new AofTask(1,taskId.toString(),System.currentTimeMillis(),timeUnit.toMillis(time),taskBody);

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

    private Long loadVersion(){
        Properties properties = propertiesLoad();
        if(properties.containsKey(this.queueAofKey) && properties.getProperty(this.queueAofKey)!=null){
            vesion = Long.parseLong(properties.getProperty(this.queueAofKey));
        }else {
            vesion = System.currentTimeMillis();
            HashMap<String,String> hashMap = new HashMap<>(1);
            hashMap.put(queueAofKey,vesion.toString());
            refreshConfig(hashMap);
        }
        return vesion;
    }

    private ObjectOutputStream getObjectOutStream() throws IOException {
        if(os != null) {
            return os;
        }

        if(vesion == null){
            if(loadVersion() == null){
                logger.debug("加载任务队列{}的aof版本好出错",this.queueAofKey);
            }
        }

        File file = new File("aof"+File.separator+this.queueAofKey+vesion+".bat");
        if(file.exists()){
            //文件存在
            FileOutputStream fo = new FileOutputStream(file,true);
            os = new ObjectOutputStream(fo);
            long pos = fo.getChannel().position()-4;
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
