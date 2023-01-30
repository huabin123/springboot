import cn.hutool.core.util.NumberUtil;
import com.github.javafaker.Faker;
import org.junit.Test;

/**
 * @Author huabin
 * @DateTime 2023-01-11 11:14
 * @Desc
 */
public class JavaFakerTest {

    @Test
    public void test(){
        Faker faker = new Faker();
        System.out.println(faker.animal().name());

        System.out.println(NumberUtil.compare(0.1, 0.01));
    }

}
