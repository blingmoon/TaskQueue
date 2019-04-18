package com.blingsun.taskqueue.taskpresist;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * description:
 * 这个class是用来维护表class的
 * @author BlingSun liang.zhou01@ucarinc.com
 * @version 1.0 2018/12/12 14:43 by BlingSun (liang.zhou01@ucarinc.com) 创建
 **/
public class TaskClassTable {
    private static final Logger logger = LoggerFactory.getLogger(TaskClassTable.class);
    public static final String SEVER_TASK_KEY;
    public static final Map<String,String> CLASS_NAME_MAP;

    private TaskClassTable(){
    }

    static {
        //加载TaskClassPersistence.properties
        Properties properties = new Properties();
        InputStream in = TaskClassTable.class.getClassLoader().getResourceAsStream("TaskClassPersistence.properties");
        propertiesLoad(properties,in);
        //设置服务的key
        String serverTaskKey = properties.getProperty("task.persistence.key","");
        if("".equals(serverTaskKey)){
            SEVER_TASK_KEY = "taskClass"+getMac();
        }else {
            SEVER_TASK_KEY = serverTaskKey;
        }

        CLASS_NAME_MAP = new HashMap<String, String>(16);
        String[] keys = properties.getProperty("task.class.key").split(",");
        if(keys.length !=0){
            for(String key:keys){
                CLASS_NAME_MAP.put(key.trim(),properties.getProperty(key).trim());
            }
        }


    }

    /**
     *  将读取的流装载到properties供内存使用
     * @param properties 装载的对象
     * @param in 流文件
     */
    private static  void propertiesLoad(Properties properties,InputStream in){
        try {
            properties.load(in);
        } catch (IOException e) {
            if(logger.isDebugEnabled()){
                logger.debug("TaskClassPersistence.properties load failed",e);
            }
        }finally {
            try {
                in.close();
            } catch (IOException e) {
                if(logger.isDebugEnabled()){
                    logger.debug("TaskClassPersistence.properties file close failed",e);
                }
            }
        }
    }

    /**
     *  获得本机的mac地址
     * @return 本机的mac
     */
    private static  String getMac(){
        InetAddress inetAddress=null;
        //获取本地IP对象
        try {
            inetAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            if(logger.isDebugEnabled()) {
                logger.debug("init TaskClassTable get inetAddress failed", e);
            }
        }

        if(inetAddress == null){
            if(logger.isDebugEnabled()){
                logger.debug("init TaskClassTable get inetAddress failed");
            }
            return "";
        }
        //获得网络接口对象（即网卡），并得到mac地址，mac地址存在于一个byte数组中。
        byte[] mac = new byte[0];
        try {
            mac = NetworkInterface.getByInetAddress(inetAddress).getHardwareAddress();
        } catch (SocketException e) {
            if(logger.isDebugEnabled()){
                logger.debug("get mac failed",e);
            }
        }

        //下面代码是把mac地址拼装成String
        StringBuilder sb = new StringBuilder();
        for (Byte temp:mac){
            sb.append(Integer.toHexString(temp&0xFF));
        }

        //把字符串所有小写字母改为大写成为正规的mac地址并返回
        return sb.toString().toUpperCase();
    }


}
