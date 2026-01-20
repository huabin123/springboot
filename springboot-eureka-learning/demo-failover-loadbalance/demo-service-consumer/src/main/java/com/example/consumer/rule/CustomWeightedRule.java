package com.example.consumer.rule;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 权重负载均衡策略
 * 根据配置的权重分配流量
 * 
 * @author Demo
 */
public class CustomWeightedRule extends AbstractLoadBalancerRule {

    private static final Logger logger = LoggerFactory.getLogger(CustomWeightedRule.class);
    private Random random = new Random();
    
    // 权重配置：端口 -> 权重
    // 实际项目中应该从配置文件读取
    private Map<Integer, Integer> weightMap = new HashMap<Integer, Integer>();

    public CustomWeightedRule() {
        // 默认权重配置
        weightMap.put(8081, 5);  // 权重5
        weightMap.put(8082, 3);  // 权重3
        weightMap.put(8083, 2);  // 权重2
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        // 可以从配置文件读取权重
    }

    @Override
    public Server choose(Object key) {
        return choose(getLoadBalancer(), key);
    }

    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            logger.warn("LoadBalancer为空");
            return null;
        }

        List<Server> servers = lb.getReachableServers();
        if (servers == null || servers.isEmpty()) {
            logger.warn("没有可用的服务实例");
            return null;
        }

        // 计算总权重
        int totalWeight = 0;
        for (Server server : servers) {
            int weight = getWeight(server);
            totalWeight += weight;
        }

        if (totalWeight == 0) {
            logger.warn("总权重为0，使用第一个实例");
            return servers.get(0);
        }

        // 随机选择
        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (Server server : servers) {
            int weight = getWeight(server);
            currentWeight += weight;
            
            if (randomWeight < currentWeight) {
                logger.info("权重策略 - 总权重: {}, 随机值: {}, 选择实例: {} (权重: {})", 
                           totalWeight, randomWeight, server.getId(), weight);
                return server;
            }
        }

        // 理论上不会到这里
        return servers.get(0);
    }

    /**
     * 获取服务器权重
     */
    private int getWeight(Server server) {
        int port = server.getPort();
        Integer weight = weightMap.get(port);
        return weight != null ? weight : 1;  // 默认权重为1
    }

    /**
     * 设置权重配置
     */
    public void setWeightMap(Map<Integer, Integer> weightMap) {
        this.weightMap = weightMap;
    }
}
