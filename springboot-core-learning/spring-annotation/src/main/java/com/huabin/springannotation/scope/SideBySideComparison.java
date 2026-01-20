package com.huabin.springannotation.scope;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 并排对比：getBean() vs ObjectProvider
 * 证明两者在递归隔离场景下行为完全一致
 */
@RestController
public class SideBySideComparison {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 测试1：使用getBean()方式
     */
    @GetMapping("/compare/getbean")
    public String testWithGetBean() {
        ProcessorWithGetBean processor = applicationContext.getBean(ProcessorWithGetBean.class);
        String result = processor.process(
            Collections.singletonList("A"), 
            Arrays.asList("B", "rank:C,D", "E", "rank:F,G")
        );
        return "使用getBean()结果: " + result;
    }

    /**
     * 测试2：使用ObjectProvider方式
     */
    @GetMapping("/compare/objectprovider")
    public String testWithObjectProvider() {
        ProcessorWithObjectProvider processor = applicationContext.getBean(ProcessorWithObjectProvider.class);
        String result = processor.process(
            Collections.singletonList("A"), 
            Arrays.asList("B", "rank:C,D", "E", "rank:F,G")
        );
        return "使用ObjectProvider结果: " + result;
    }

    /**
     * 测试3：验证两者结果一致性
     */
    @GetMapping("/compare/verify")
    public String verifyConsistency() {
        // 使用getBean()
        ProcessorWithGetBean processor1 = applicationContext.getBean(ProcessorWithGetBean.class);
        String result1 = processor1.process(
            Collections.singletonList("1"), 
            Arrays.asList("2", "rank:3,4")
        );

        // 使用ObjectProvider
        ProcessorWithObjectProvider processor2 = applicationContext.getBean(ProcessorWithObjectProvider.class);
        String result2 = processor2.process(
            Collections.singletonList("1"), 
            Arrays.asList("2", "rank:3,4")
        );

        boolean isEqual = result1.equals(result2);
        
        return String.format(
            "getBean()结果: %s\n" +
            "ObjectProvider结果: %s\n" +
            "结果是否一致: %s ✅",
            result1, result2, isEqual
        );
    }

    // ==================== 使用 getBean() 的实现 ====================

    @Scope("prototype")
    @Component
    public static class ProcessorWithGetBean {
        
        @Autowired
        private ApplicationContext applicationContext;

        private List<String> listA;
        private List<String> listB;
        private List<String> listAll;
        private int instanceId;

        public ProcessorWithGetBean() {
            this.instanceId = System.identityHashCode(this);
            System.out.println("🔵 [getBean] 创建新实例: " + instanceId);
        }

        private void initializeState() {
            this.listA = new ArrayList<>();
            this.listB = new ArrayList<>();
            this.listAll = new ArrayList<>();
        }

        public String process(List<String> a, List<String> b) {
            initializeState();
            
            System.out.println("🔵 [getBean] 实例" + instanceId + " 开始处理: a=" + a + ", b=" + b);
            
            // 处理列表A
            listA.addAll(a);
            
            // 处理列表B（包含递归）
            for (String item : b) {
                if (item.startsWith("rank:")) {
                    String[] parts = item.substring(5).split(",");
                    
                    System.out.println("🔵 [getBean] 实例" + instanceId + " 触发递归: " + item);
                    
                    // 关键：使用getBean()创建新实例
                    ProcessorWithGetBean recursiveInstance = 
                        applicationContext.getBean(ProcessorWithGetBean.class);
                    
                    String recursiveResult = recursiveInstance.process(
                        Collections.singletonList(parts[0]),
                        Collections.singletonList(parts[1])
                    );
                    
                    listB.add("[" + recursiveResult + "]");
                    System.out.println("🔵 [getBean] 实例" + instanceId + " 递归返回: " + recursiveResult);
                } else {
                    listB.add(item);
                }
            }
            
            // 合并结果
            listAll.addAll(listA);
            listAll.addAll(listB);
            
            String result = String.join(",", listAll);
            System.out.println("🔵 [getBean] 实例" + instanceId + " 完成处理: " + result);
            
            return result;
        }
    }

    // ==================== 使用 ObjectProvider 的实现 ====================

    @Scope("prototype")
    @Component
    public static class ProcessorWithObjectProvider {
        
        @Autowired
        private ObjectProvider<ProcessorWithObjectProvider> objectProvider;

        private List<String> listA;
        private List<String> listB;
        private List<String> listAll;
        private int instanceId;

        public ProcessorWithObjectProvider() {
            this.instanceId = System.identityHashCode(this);
            System.out.println("🟢 [ObjectProvider] 创建新实例: " + instanceId);
        }

        private void initializeState() {
            this.listA = new ArrayList<>();
            this.listB = new ArrayList<>();
            this.listAll = new ArrayList<>();
        }

        public String process(List<String> a, List<String> b) {
            initializeState();
            
            System.out.println("🟢 [ObjectProvider] 实例" + instanceId + " 开始处理: a=" + a + ", b=" + b);
            
            // 处理列表A
            listA.addAll(a);
            
            // 处理列表B（包含递归）
            for (String item : b) {
                if (item.startsWith("rank:")) {
                    String[] parts = item.substring(5).split(",");
                    
                    System.out.println("🟢 [ObjectProvider] 实例" + instanceId + " 触发递归: " + item);
                    
                    // 关键：使用ObjectProvider.getObject()创建新实例
                    ProcessorWithObjectProvider recursiveInstance = 
                        objectProvider.getObject();
                    
                    String recursiveResult = recursiveInstance.process(
                        Collections.singletonList(parts[0]),
                        Collections.singletonList(parts[1])
                    );
                    
                    listB.add("[" + recursiveResult + "]");
                    System.out.println("🟢 [ObjectProvider] 实例" + instanceId + " 递归返回: " + recursiveResult);
                } else {
                    listB.add(item);
                }
            }
            
            // 合并结果
            listAll.addAll(listA);
            listAll.addAll(listB);
            
            String result = String.join(",", listAll);
            System.out.println("🟢 [ObjectProvider] 实例" + instanceId + " 完成处理: " + result);
            
            return result;
        }
    }

    // ==================== ObjectProvider的额外优势演示 ====================

    @Scope("prototype")
    @Component
    public static class ProcessorWithObjectProviderAdvanced {
        
        @Autowired
        private ObjectProvider<ProcessorWithObjectProviderAdvanced> objectProvider;

        private List<String> data = new ArrayList<>();

        /**
         * 演示ObjectProvider的额外便利方法
         */
        public void demonstrateAdvantages() {
            // 1. 安全获取（找不到返回null而不是异常）
            ProcessorWithObjectProviderAdvanced instance1 = objectProvider.getIfAvailable();
            if (instance1 != null) {
                System.out.println("✅ 成功获取实例");
            }

            // 2. 带默认值的获取
            ProcessorWithObjectProviderAdvanced instance2 = objectProvider.getIfAvailable(() -> {
                System.out.println("⚠️ 找不到Bean，使用默认实例");
                return new ProcessorWithObjectProviderAdvanced();
            });

            // 3. 获取唯一Bean（多个时抛异常）
            ProcessorWithObjectProviderAdvanced instance3 = objectProvider.getIfUnique();

            // 4. 流式处理（处理所有匹配的Bean）
            objectProvider.stream()
                .limit(5)
                .forEach(processor -> {
                    System.out.println("处理实例: " + processor.instanceId);
                });

            // 5. forEach遍历
            objectProvider.forEach(processor -> {
                processor.data.add("processed");
            });
        }

        private int instanceId = System.identityHashCode(this);
    }
}
