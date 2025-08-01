package com.huabin.springannotation.scope;

import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@RequestScope
@RestController
public class SpringRequestScopeDemoV2 {

    // 假设有大量成员变量（示例仅展示部分）
    private List<String> listA = new ArrayList<>();
    private List<String> listB = new ArrayList<>();
    private Map<String, Integer> counters = new HashMap<>();
    private Set<String> processedItems = new HashSet<>();
    private StringBuilder logBuilder = new StringBuilder();
    // ... 其他几十个成员变量

    private int recursionDepth = 0;

    @PostMapping("/process")
    public String handleRequest(@RequestBody RequestData request) {
        // 请求开始时所有成员变量已自动初始化
        return mainMethod(request.getA(), request.getB());
    }

    private String mainMethod(List<String> a, List<String> b) {
        recursionDepth++;

        // 1. 创建当前状态快照
        StateSnapshot snapshot = new StateSnapshot(this);

        try {
            // 2. 处理当前层级数据
            processA(a);
            processB(b);

            // 3. 构建结果
            return buildResult();
        } finally {
            // 4. 状态恢复（仅内层递归需要）
            if (recursionDepth > 1) {
                snapshot.restoreState(this);
            }
            recursionDepth--;
        }
    }

    // =============== 状态快照类 ===============
    private static class StateSnapshot {
        private final Map<String, Object> stateMap = new HashMap<>();

        public StateSnapshot(SpringRequestScopeDemoV2 instance) {
            // 使用反射获取所有字段状态
            for (Field field : SpringRequestScopeDemoV2.class.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(instance);

                    // 深拷贝常见集合类型
                    if (value instanceof List) {
                        stateMap.put(field.getName(), new ArrayList<>((List<?>) value));
                    } else if (value instanceof Set) {
                        stateMap.put(field.getName(), new HashSet<>((Set<?>) value));
                    } else if (value instanceof Map) {
                        stateMap.put(field.getName(), new HashMap<>((Map<?, ?>) value));
                    } else if (value instanceof StringBuilder) {
                        stateMap.put(field.getName(), new StringBuilder(value.toString()));
                    } else if (value instanceof Cloneable) {
                        // 自定义克隆逻辑（如有）
                    } else {
                        stateMap.put(field.getName(), value);
                    }
                } catch (IllegalAccessException e) {
                    // 记录错误或抛出运行时异常
                }
            }
        }

        public void restoreState(SpringRequestScopeDemoV2 instance) {
            stateMap.forEach((fieldName, savedValue) -> {
                try {
                    Field field = SpringRequestScopeDemoV2.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(instance, savedValue);
                } catch (Exception e) {
                    // 错误处理
                }
            });
        }
    }

    // =============== 业务处理方法 ===============
    private void processA(List<String> a) {
        listA.addAll(a);
        // 更新其他相关状态...
    }

    private void processB(List<String> b) {
        for (String item : b) {
            if (item.startsWith("rank:")) {
                handleRankItem(item);
            } else {
                listB.add(item);
                processedItems.add(item);
            }
        }
    }

    private void handleRankItem(String rankItem) {
        String[] parts = rankItem.substring(5).split(",");
        String recurseResult = mainMethod(
                Collections.singletonList(parts[0]),
                Collections.singletonList(parts[1])
        );
        listB.add(recurseResult);

        // 更新计数器
        counters.merge("rank_calls", 1, Integer::sum);
        logBuilder.append("Processed rank: ").append(rankItem).append("\n");
    }

    private String buildResult() {
        // 组合多个状态构建结果
        return listA.stream().collect(Collectors.joining()) +
                listB.stream().collect(Collectors.joining()) +
                " | Count: " + counters.getOrDefault("rank_calls", 0);
    }

    // 请求数据结构
    @Data
    static class RequestData {
        private List<String> a;
        private List<String> b;

        // getters/setters
    }
}
