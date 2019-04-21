import com.blingsun.taskqueue.TaskBody;

import java.io.Serializable;

/**
 * description:
 *
 * @author zhouliang 297977761@qq.com
 * @version 1.0 2019/4/21  by zhouliang 297977761@qq.com 创建
 */
public class AofTaskBody implements Serializable, TaskBody {
    private String id;

    public AofTaskBody(String id) {
        this.id = id;
    }

    @Override
    public void execute() {
        System.out.println("test"+id);
    }
}
