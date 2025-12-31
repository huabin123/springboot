package com.huabin.springannotation.scope;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Scope("prototype")
@RestController
public class SpringRequestScopeDemoV3 {

    @Autowired
    private ApplicationContext applicationContext;

    // 类变量声明（不需要初始化）
    private List<String> listA;
    private List<String> listB;
    private List<String> listAll;

    // 初始化方法（确保每次递归都有干净状态）
    private void initializeState() {
        this.listA = new ArrayList<>();
        this.listB = new ArrayList<>();
        this.listAll = new ArrayList<>();
    }

    public void methodA(List<String> a) {
        listA.addAll(a);
    }

    public void methodB(List<String> b) {
        for (String s : b) {
            if (s.contains("rank")) {
                String[] split = s.split(":");
                String s1 = split[1];
                String[] split1 = s1.split(",");

                // 递归调用：创建新实例处理
                SpringRequestScopeDemoV3 recursiveProcessor = applicationContext.getBean(SpringRequestScopeDemoV3.class);
                String result = recursiveProcessor.mainMethod(
                        Collections.singletonList(split1[0]),
                        Collections.singletonList(split1[1])
                );

                listB.add(result);
            } else {
                listB.add(s);
            }
        }
    }

    public void methodAddA() {
        listAll.addAll(listA);
    }

    public void methodAddB() {
        listAll.addAll(listB);
    }

    public String mainMethod(List<String> a, List<String> b) {
        // 初始化当前实例的状态
        initializeState();

        methodA(a);
        methodB(b);
        methodAddA();

        StringBuilder stringBuilder = new StringBuilder();
        for (String s : listAll) {
            stringBuilder.append(s);
        }
        methodAddB();
        return stringBuilder.toString();
    }

    @PostMapping("/SpringScopeDemo/test3")
    @ResponseBody
    public String SpringScopeDemoController() {
        // 初始化顶层状态
        return this.mainMethod(Collections.singletonList("1"), Arrays.asList("2", "rank:2,3"));
    }
}
