package com.example.consumer.config;

import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.RoundRobinRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ribbon配置
 * 
 * 可以切换不同的负载均衡策略进行测试：
 * - RoundRobinRule: 轮询
 * - RandomRule: 随机
 * - WeightedResponseTimeRule: 响应时间加权
 * - RetryRule: 重试
 * - BestAvailableRule: 最低并发
 * - AvailabilityFilteringRule: 可用性过滤
 * - ZoneAvoidanceRule: 区域避让（默认）
 * - CustomIpHashRule: 自定义IP Hash
 * - CustomWeightedRule: 自定义权重
 * - CustomGrayReleaseRule: 自定义灰度发布
 * 
 * @author Demo
 */
@Configuration
public class RibbonConfig {

    /**
     * 配置负载均衡策略
     * 
     * 取消注释不同的策略进行测试
     */
    @Bean
    public IRule ribbonRule() {
        // 默认使用轮询策略
        return new RoundRobinRule();
        
        // 随机策略
        // return new RandomRule();
        
        // 响应时间加权策略
        // return new WeightedResponseTimeRule();
        
        // 重试策略
        // return new RetryRule();
        
        // 最低并发策略
        // return new BestAvailableRule();
        
        // 可用性过滤策略
        // return new AvailabilityFilteringRule();
        
        // 区域避让策略（默认）
        // return new ZoneAvoidanceRule();
        
        // 自定义IP Hash策略
        // return new CustomIpHashRule();
        
        // 自定义权重策略
        // return new CustomWeightedRule();
        
        // 自定义灰度发布策略
        // return new CustomGrayReleaseRule();
    }
}
