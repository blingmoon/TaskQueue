package com.blingsun.taskqueue;

import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractTaskQueue extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTaskQueue.class);

    private final AtomicInteger currentIndex=new AtomicInteger(0);

    /**
     * 必须采用公平锁,防止长时间的抢不到lock去更新index
     */
    private final ReentrantLock indexLock = new ReentrantLock(true);

    //Slot的越长,碰撞的可能性就越小，插入的效率也会越高
    private final  Slot[] slots;
    private final long minStep;

    /**
     * 记录当前的时间,任务队列中使用,配合minStep来判断下一个执行时间
     */
    private long currentTime;

    /**
     * 任务池,放在这边主要的目的是为了除移操作
     */
    private Map<Object,Task> taskPool=new ConcurrentHashMap<>(16);

    /**
     * TaskQueue构造函数
     * @param slotsLength 和slots的长度
     * @param timeAccuracy 时间精度大小
     * @param timeUnit 时间单位,可以说是 h,million,day等,具体的看自己需要的精度
     * eg: (3600 , 1 , SECONDS)这三个参数就表示slot的长度是3600,没过1s检查一次任务队列
     */
    public AbstractTaskQueue(Integer slotsLength,Integer timeAccuracy,TimeUnit timeUnit){
        this.currentTime = System.currentTimeMillis();
        this.slots=new Slot[slotsLength];
        this.minStep=timeUnit.toMillis(timeAccuracy);
    }


    /**
     * 这个是开始的时候加载动作,如果持久化了的话,在启动的时候需要去实现这个函数
     */
    public abstract void loadHistory();

    /**
     * 用来实现持久化任务,重写这个方法可以进行持久化的方式选择
     * @param taskId 任务的id
     * @param taskBody 任务的执行题
     * @param time 定时的时间
     * @param timeUnit 定时的时间单位
     * @return 是否成功持久化，成功为TRUE,失败返回False
     */
     protected abstract Boolean persistenceTask(Object taskId,TaskBody taskBody,Long time,TimeUnit timeUnit);

    /**
     * 用来实现删除持久化任务,在remove使用到
     * @param taskId 任务的id
     * @return 是否成功删除，成功为TRUE,失败返回False
     */
    protected abstract Boolean deleteTask(Object taskId);

    /**
     * 自己定义任务的执行过程,可以是同步，异步,也可以是线程池执行,取决于自身的实现
     * @param taskBody 任务执行体
     */
    protected abstract void excuteTask(TaskBody taskBody);

    /**
     * 默认的构造函数是以1s为步长，一个小时即3600s为一个周期
     */
    public AbstractTaskQueue(){
        this(3600,1,TimeUnit.SECONDS);
    }

    /**
     * 向slots中添加任务
     * @param taskId 任务的id,请保持唯一
     * @param taskBody 任务的执行体
     * @param time  多长时候执行任务
     * @param timeUnit 时间单位
     */
    public  void registerTask(Object taskId,TaskBody taskBody,Long time,TimeUnit timeUnit){

        //1.计算添加的slots的位置
        Integer steps = (int) (timeUnit.toMillis(time)/minStep);
        //2.计算周期数
        Integer cycleNum=steps/slots.length;

        //持久化任务选择
        if(!persistenceTask(taskId,taskBody,time,timeUnit)){
            //没有成功持久化
            if(logger.isDebugEnabled()){
                logger.debug("任务 taskId {},taskBody {},没有成功持久化",taskId,taskBody);
            }
        }

        indexLock.lock();
        //这个会和run进行竞争,主要是为了维持currentIndex的线程安全
        try{
            Integer current = currentIndex.get();
            Integer index = (current + steps) % slots.length;
            //3.向指定的TaskPool添加任务
            taskPool.put(taskId, new Task(taskBody, cycleNum, index));
            //4.将键添加到维护队列中
            if (slots[index] == null) {
                slots[index] = new Slot();
            }
            slots[index].addKey(taskId);
        }finally {
            indexLock.unlock();
        }

        if(logger.isDebugEnabled()){
            logger.debug("任务{}已经被添加到延迟队列,任务将在{}s后触发",taskId,time);
        }

    }

    /**
     * 在任务池中除移任务
     * @param key 除移的任务键
     */
    public void removeTask(Object key){
        Task task = taskPool.remove(key);
        if(task == null){
            return;
        }
        //删除任务持久化任务
        if(deleteTask(key)&&logger.isDebugEnabled()){
            logger.debug("任务{}已经被除移",key);
        }
    }

    /**
     * 用来响应暂停中断
     * @param e 中断
     */
    private void handleInterruptedException(Exception e){
        if(logger.isDebugEnabled()){
            logger.debug("InterruptedException {}",e);
        }
    }

    /**
     * 定时任务的执行体
     * 注意一点,这个和注册任务是存在冲突的,是需要去加锁
     */
    @Override
    public  void run() {
        while (true) {
            indexLock.lock();
            try {
                //1.检查当前的slots[currentIndex]
                if (slots[currentIndex.get()] != null) {
                    Iterator iterator = slots[currentIndex.get()].getSetIterator();
                    Object key;
                    Task temp;

                    //遍历set的键,同时完成指定操作
                    while (iterator.hasNext()) {
                        key = iterator.next();
                        temp = taskPool.get(key);
                        if (temp == null) {
                            //空的temp 说明任务以及被移除了,将slots中的键移除
                            iterator.remove();
                        } else {
                            //不是空的要进行处理
                            //checkTask的逻辑先将循环自减
                            //<0返回true, >=0返回false
                            if (temp.checkTask()) {
                                taskPool.remove(key);
                                iterator.remove();
                                excuteTask(temp.taskBody);
                            }
                        }
                    }
                }
                //2.维护currentIndex指针
                currentIndex.set(currentIndex.incrementAndGet() % slots.length);
            }finally {
                indexLock.unlock();
            }

            //线程休息时间
            //这一段主要是为了防止上面执行的时间过长导致定时出现偏差，从而进行修复的操作
            long nowTime = System.currentTimeMillis();
            long nextTime = currentTime + minStep - nowTime >0? currentTime + minStep - nowTime:0;
            currentTime = nowTime;
            if(nextTime!=0){
                try {
                    Thread.sleep(nextTime);
                } catch (InterruptedException e) {
                    handleInterruptedException(e);
                }
            }

        }

    }


    public long getRestTime(Object key){
        Task  task=taskPool.get(key);

        if(task==null){
            if(logger.isDebugEnabled()){
                logger.debug("task:{}已经被移除了",key);
            }
            return 0;
        }

        long cycleTime=this.slots.length*this.minStep;
        long restTime = 0;
        indexLock.lock();
        try {
             restTime = cycleTime*task.cycleNum+(task.index+this.slots.length-this.currentIndex.get())%cycleTime;
        }finally {
            indexLock.unlock();

        }
        return restTime;
    }


    /**
     * description:
     * 具体的任务,需要有任务的执行体，以及扫描循环次数
     * @author BlingSun liang.zhou01@ucarinc.com
     * @version 2018/9/30 14:30
     * @since 2018/9/30 14:30
     **/
    class Task {

        private TaskBody taskBody;
        private Integer cycleNum;

        /**
         * 这个是Task在slot数组的下标
         */
        private Integer index;


        Task(TaskBody taskBody, Integer cycleNum,Integer index) {
            this.taskBody = taskBody;
            this.cycleNum = cycleNum;
            this.index=index;
        }




        /**
         *checkTask的逻辑先将循环自减
         * cycleNum<=0返回true,>0返回false
         * 两者顺序不可以颠倒会出现-1的情况当定时任务正好为一个周期情况回收-1执行
         */
        Boolean checkTask(){
            --cycleNum;
            return  cycleNum<0;
        }

        /**
         * 执行任务体
         */
        void excuteTask(){
            taskBody.execute();
        }

        @Override
        public String toString() {
            return "Task{" +
                    "taskBody=" + taskBody +
                    ", cycleNum=" + cycleNum +
                    ", index=" + index +
                    '}';
        }
    }


    /**
     * description:
     * TaskQueue的节点,主要是存放任务键的集合
     * @author BlingSun liang.zhou01@ucarinc.com
     * @version 2018/9/30 14:29
     * @since 2018/9/30 14:29
     **/
    class Slot {

        /**
         * 请保持Set的线程安全
         */
        private Set<Object> taskKeys=Collections.synchronizedSet(new HashSet<>());

        void  addKey(Object key){
            taskKeys.add(key);
        }

        Iterator getSetIterator(){
            return taskKeys.iterator();
        }

    }

}

