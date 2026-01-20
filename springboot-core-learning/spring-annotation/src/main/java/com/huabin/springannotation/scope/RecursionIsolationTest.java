package com.huabin.springannotation.scope;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 递归隔离测试：对比不同scope和获取方式的行为
 */
@RestController
public class RecursionIsolationTest {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 测试1：Prototype Scope + getBean()
     * 结果：✅ 完美隔离，每次递归都是新实例
     */
    @GetMapping("/test/prototype-getbean")
    public String testPrototypeWithGetBean() {
        PrototypeProcessor processor = applicationContext.getBean(PrototypeProcessor.class);
        String result = processor.process(Arrays.asList("A"), Arrays.asList("B", "rank:C,D"));
        return "Prototype+getBean: " + result;
    }

    /**
     * 测试2：Prototype Scope + ObjectProvider
     * 结果：✅ 完美隔离，每次递归都是新实例（与getBean()行为一致）
     */
    @GetMapping("/test/prototype-objectprovider")
    public String testPrototypeWithObjectProvider() {
        PrototypeProcessorWithProvider processor = applicationContext.getBean(PrototypeProcessorWithProvider.class);
        String result = processor.process(Arrays.asList("A"), Arrays.asList("B", "rank:C,D"));
        return "Prototype+ObjectProvider: " + result;
    }

    /**
     * 测试3：Request Scope + getBean()（错误示例）
     * 结果：❌ 状态污染，递归时获取同一实例
     */
    @GetMapping("/test/request-getbean-wrong")
    public String testRequestWithGetBeanWrong() {
        RequestProcessorWrong processor = applicationContext.getBean(RequestProcessorWrong.class);
        String result = processor.process(Arrays.asList("A"), Arrays.asList("B", "rank:C,D"));
        return "Request+getBean(Wrong): " + result;
    }

    /**
     * 测试4：Request Scope + getBean() + 状态快照（正确示例）
     * 结果：✅ 通过状态快照实现隔离
     */
    @GetMapping("/test/request-getbean-correct")
    public String testRequestWithGetBeanCorrect() {
        RequestProcessorCorrect processor = applicationContext.getBean(RequestProcessorCorrect.class);
        String result = processor.process(Arrays.asList("A"), Arrays.asList("B", "rank:C,D"));
        return "Request+getBean(Correct): " + result;
    }

    // ==================== Prototype Scope 实现 ====================

    @Scope("prototype")
    public static class PrototypeProcessor {
        @Autowired
        private ApplicationContext applicationContext;

        private List<String> data = new ArrayList<>();

        public String process(List<String> a, List<String> b) {
            System.out.println("PrototypeProcessor实例: " + System.identityHashCode(this));
            
            data.addAll(a);
            
            for (String item : b) {
                if (item.startsWith("rank:")) {
                    String[] parts = item.substring(5).split(",");
                    // 每次递归创建新实例
                    PrototypeProcessor newInstance = applicationContext.getBean(PrototypeProcessor.class);
                    String recursiveResult = newInstance.process(Arrays.asList(parts[0]), Arrays.asList(parts[1]));
                    data.add("[" + recursiveResult + "]");
                } else {
                    data.add(item);
                }
            }
            
            return String.join(",", data);
        }
    }

    @Scope("prototype")
    public static class PrototypeProcessorWithProvider {
        @Autowired
        private ObjectProvider<PrototypeProcessorWithProvider> objectProvider;

        private List<String> data = new ArrayList<>();

        public String process(List<String> a, List<String> b) {
            System.out.println("PrototypeProcessorWithProvider实例: " + System.identityHashCode(this));
            
            data.addAll(a);
            
            for (String item : b) {
                if (item.startsWith("rank:")) {
                    String[] parts = item.substring(5).split(",");
                    // ObjectProvider.getObject()对于prototype也是创建新实例
                    PrototypeProcessorWithProvider newInstance = objectProvider.getObject();
                    String recursiveResult = newInstance.process(Arrays.asList(parts[0]), Arrays.asList(parts[1]));
                    data.add("[" + recursiveResult + "]");
                } else {
                    data.add(item);
                }
            }
            
            return String.join(",", data);
        }
    }

    // ==================== Request Scope 错误实现 ====================

    @RequestScope
    public static class RequestProcessorWrong {
        @Autowired
        private ApplicationContext applicationContext;

        private List<String> data = new ArrayList<>();
        private int recursionLevel = 0;

        public String process(List<String> a, List<String> b) {
            recursionLevel++;
            System.out.println("RequestProcessorWrong实例: " + System.identityHashCode(this) + 
                             ", 递归层级: " + recursionLevel);
            
            // 问题：递归时会清空外层数据
            data.clear();
            data.addAll(a);
            
            for (String item : b) {
                if (item.startsWith("rank:")) {
                    String[] parts = item.substring(5).split(",");
                    // getBean()返回同一实例（request scope）
                    RequestProcessorWrong sameInstance = applicationContext.getBean(RequestProcessorWrong.class);
                    System.out.println("是否同一实例: " + (sameInstance == this));
                    String recursiveResult = sameInstance.process(Arrays.asList(parts[0]), Arrays.asList(parts[1]));
                    data.add("[" + recursiveResult + "]");
                } else {
                    data.add(item);
                }
            }
            
            recursionLevel--;
            return String.join(",", data);
        }
    }

    // ==================== Request Scope 正确实现 ====================

    @RequestScope
    public static class RequestProcessorCorrect {
        @Autowired
        private ApplicationContext applicationContext;

        private List<String> data = new ArrayList<>();
        private int recursionLevel = 0;

        public String process(List<String> a, List<String> b) {
            recursionLevel++;
            System.out.println("RequestProcessorCorrect实例: " + System.identityHashCode(this) + 
                             ", 递归层级: " + recursionLevel);
            
            // 保存当前状态
            List<String> savedData = new ArrayList<>(data);
            
            try {
                data.clear();
                data.addAll(a);
                
                for (String item : b) {
                    if (item.startsWith("rank:")) {
                        String[] parts = item.substring(5).split(",");
                        RequestProcessorCorrect sameInstance = applicationContext.getBean(RequestProcessorCorrect.class);
                        String recursiveResult = sameInstance.process(Arrays.asList(parts[0]), Arrays.asList(parts[1]));
                        data.add("[" + recursiveResult + "]");
                    } else {
                        data.add(item);
                    }
                }
                
                return String.join(",", data);
            } finally {
                // 恢复状态（仅内层递归需要）
                if (recursionLevel > 1) {
                    data = savedData;
                }
                recursionLevel--;
            }
        }
    }
}
