package com.huabin.springannotation.scope;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-07-31 17:07
 * @Desc
 */

@RequestScope
@RestController
public class SpringRequestScopeDemo {

    List<String> listA = new ArrayList<>();
    List<String> listB = new ArrayList<>();
    List<String> listAll = new ArrayList<>();

    public void methodA(List<String> a){
        for (String s : a) {
            listA.add(s);
        }
    }

    public void methodB(List<String> b) {
        for (String s : b) {
            if (s.contains("rank")) {
                String[] split = s.split(":");
                String s1 = split[1];
                String[] split1 = s1.split(",");
                listB.add(this.mainMethod(Arrays.asList(split1[0]), Arrays.asList(split1[1])));
            } else {
                listB.add(s);
            }
        }
    }

    public void methodC() {
        listAll.addAll(listA);
        listAll.addAll(listB);
    }

    public String mainMethod(List<String> a, List<String> b) {
        methodA(a);
        methodB(b);
        methodC();
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : listAll) {
            stringBuilder.append(s);
        }
        return stringBuilder.toString();
    }

    @PostMapping("/SpringScopeDemo/test")
    @ResponseBody
    public String SpringScopeDemoController(){
        return this.mainMethod(Arrays.asList("1"), Arrays.asList("2", "rank:2,3"));
    }
}
