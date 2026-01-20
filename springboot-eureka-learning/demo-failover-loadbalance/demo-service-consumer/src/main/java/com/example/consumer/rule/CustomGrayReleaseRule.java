package com.example.consumer.rule;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 灰度发布负载均衡策略
 * 根据请求头中的版本标识路由到不同版本的服务实例
 * 
 * @author Demo
 */
public class CustomGrayReleaseRule extends AbstractLoadBalancerRule {

    private static final Logger logger = LoggerFactory.getLogger(CustomGrayReleaseRule.class);
    private static final String GRAY_VERSION_HEADER = "X-Gray-Version";
    private static final String METADATA_VERSION_KEY = "version";
    private Random random = new Random();

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

        // 获取灰度版本标识
        String grayVersion = getGrayVersion();
        
        if (grayVersion != null && !grayVersion.isEmpty()) {
            // 过滤出指定版本的实例
            List<Server> grayServers = filterServersByVersion(servers, grayVersion);
            
            if (!grayServers.isEmpty()) {
                Server chosen = chooseFromServers(grayServers);
                logger.info("灰度策略 - 请求版本: {}, 选择灰度实例: {}", grayVersion, chosen.getId());
                return chosen;
            } else {
                logger.warn("灰度策略 - 未找到版本 {} 的实例，降级到稳定版", grayVersion);
            }
        }

        // 默认路由到稳定版
        List<Server> stableServers = filterStableServers(servers);
        if (!stableServers.isEmpty()) {
            Server chosen = chooseFromServers(stableServers);
            logger.info("灰度策略 - 选择稳定版实例: {}", chosen.getId());
            return chosen;
        }

        // 如果没有稳定版，返回任意实例
        Server chosen = servers.get(0);
        logger.info("灰度策略 - 选择默认实例: {}", chosen.getId());
        return chosen;
    }

    /**
     * 获取灰度版本标识
     */
    private String getGrayVersion() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader(GRAY_VERSION_HEADER);
            }
        } catch (Exception e) {
            logger.error("获取灰度版本失败", e);
        }
        
        return null;
    }

    /**
     * 根据版本过滤服务实例
     */
    private List<Server> filterServersByVersion(List<Server> servers, String version) {
        List<Server> result = new ArrayList<Server>();
        
        for (Server server : servers) {
            if (server instanceof DiscoveryEnabledServer) {
                DiscoveryEnabledServer discoveryServer = (DiscoveryEnabledServer) server;
                String serverVersion = discoveryServer.getInstanceInfo()
                    .getMetadata().get(METADATA_VERSION_KEY);
                
                if (version.equals(serverVersion)) {
                    result.add(server);
                }
            }
        }
        
        return result;
    }

    /**
     * 过滤稳定版服务实例
     */
    private List<Server> filterStableServers(List<Server> servers) {
        List<Server> result = new ArrayList<Server>();
        
        for (Server server : servers) {
            if (server instanceof DiscoveryEnabledServer) {
                DiscoveryEnabledServer discoveryServer = (DiscoveryEnabledServer) server;
                String serverVersion = discoveryServer.getInstanceInfo()
                    .getMetadata().get(METADATA_VERSION_KEY);
                
                // 版本为stable或未设置版本的实例
                if (serverVersion == null || "stable".equals(serverVersion)) {
                    result.add(server);
                }
            } else {
                // 非DiscoveryEnabledServer，视为稳定版
                result.add(server);
            }
        }
        
        return result;
    }

    /**
     * 从服务列表中随机选择一个
     */
    private Server chooseFromServers(List<Server> servers) {
        if (servers.isEmpty()) {
            return null;
        }
        
        int index = random.nextInt(servers.size());
        return servers.get(index);
    }
}
