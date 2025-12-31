package com.huabin.springannotation.scope;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 对比演示：applicationContext.getBean() vs ObjectProvider
 * 
 * 核心区别：
 * 1. applicationContext.getBean(Class) - 每次调用都创建新实例（对于prototype scope）
 * 2. ObjectProvider.getObject() - 根据scope策略获取实例
 * 
 * 在递归场景下的行为差异：
 * - prototype scope + getBean(): 每次递归创建全新实例，天然隔离
 * - request scope + getBean(): 同一请求内返回同一实例，递归时共享状态（需要手动快照恢复）
 * - ObjectProvider: 行为与直接getBean()一致，但提供了更多便利方法
 */
@Scope("prototype")
@RestController
public class ComparisonDemo {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectProvider<ComparisonDemo> objectProvider;

    private List<String> listA;
    private List<String> listB;
    private List<String> listAll;

    private void initializeState() {
        this.listA = new ArrayList<>();
        this.listB = new ArrayList<>();
        this.listAll = new ArrayList<>();
    }

    // ============ 方式1：使用 applicationContext.getBean() ============
    public String processWithGetBean(List<String> a, List<String> b) {
        initializeState();
        
        listA.addAll(a);
        
        for (String s : b) {
            if (s.contains("rank")) {
                String[] split = s.split(":");
                String s1 = split[1];
                String[] split1 = s1.split(",");

                // 关键点：每次调用getBean()都创建新的prototype实例
                // 新实例有独立的listA, listB, listAll变量
                ComparisonDemo recursiveInstance = applicationContext.getBean(ComparisonDemo.class);
                String result = recursiveInstance.processWithGetBean(
                        Collections.singletonList(split1[0]),
                        Collections.singletonList(split1[1])
                );
                
                listB.add(result);
            } else {
                listB.add(s);
            }
        }
        
        listAll.addAll(listA);
        listAll.addAll(listB);
        
        StringBuilder sb = new StringBuilder();
        for (String s : listAll) {
            sb.append(s);
        }
        return sb.toString();
    }

    // ============ 方式2：使用 ObjectProvider ============
    public String processWithObjectProvider(List<String> a, List<String> b) {
        initializeState();
        
        listA.addAll(a);
        
        for (String s : b) {
            if (s.contains("rank")) {
                String[] split = s.split(":");
                String s1 = split[1];
                String[] split1 = s1.split(",");

                // ObjectProvider.getObject() 对于prototype scope行为与getBean()相同
                // 但提供了更多便利方法：getIfAvailable(), getIfUnique(), stream()等
                ComparisonDemo recursiveInstance = objectProvider.getObject();
                String result = recursiveInstance.processWithObjectProvider(
                        Collections.singletonList(split1[0]),
                        Collections.singletonList(split1[1])
                );
                
                listB.add(result);
            } else {
                listB.add(s);
            }
        }
        
        listAll.addAll(listA);
        listAll.addAll(listB);
        
        StringBuilder sb = new StringBuilder();
        for (String s : listAll) {
            sb.append(s);
        }
        return sb.toString();
    }

    @PostMapping("/comparison/getBean")
    @ResponseBody
    public String testGetBean() {
        return this.processWithGetBean(
                Collections.singletonList("1"), 
                Arrays.asList("2", "rank:2,3")
        );
    }

    @PostMapping("/comparison/objectProvider")
    @ResponseBody
    public String testObjectProvider() {
        return this.processWithObjectProvider(
                Collections.singletonList("1"), 
                Arrays.asList("2", "rank:2,3")
        );
    }
}
