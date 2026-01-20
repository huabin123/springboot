package com.example.consumer.rule;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * IP Hash负载均衡策略
 * 根据客户端IP选择固定的服务实例，实现会话保持
 * 
 * @author Demo
 */
public class CustomIpHashRule extends AbstractLoadBalancerRule {

    private static final Logger logger = LoggerFactory.getLogger(CustomIpHashRule.class);

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        // 初始化配置
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

        // 获取客户端IP
        String clientIp = getClientIp();
        if (clientIp == null) {
            logger.warn("无法获取客户端IP，使用第一个实例");
            return servers.get(0);
        }

        // 根据IP的Hash值选择实例
        int hash = Math.abs(clientIp.hashCode());
        int index = hash % servers.size();
        
        Server chosen = servers.get(index);
        logger.info("IP Hash策略 - 客户端IP: {}, Hash: {}, 选择实例: {}", 
                    clientIp, hash, chosen.getId());
        
        return chosen;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // 尝试从X-Forwarded-For获取
                String ip = request.getHeader("X-Forwarded-For");
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    // 多次反向代理后会有多个IP值，第一个为真实IP
                    int index = ip.indexOf(',');
                    if (index != -1) {
                        return ip.substring(0, index);
                    } else {
                        return ip;
                    }
                }
                
                // 尝试从X-Real-IP获取
                ip = request.getHeader("X-Real-IP");
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
                
                // 从RemoteAddr获取
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            logger.error("获取客户端IP失败", e);
        }
        
        return null;
    }
}
