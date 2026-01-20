package com.huabin.eureka.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring Security配置类
 * 实现Eureka Server的HTTP Basic认证
 * 
 * @author huabin
 * @date 2024-01-19
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    /**
     * 配置HTTP安全规则
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // 关闭CSRF保护
            // Eureka使用REST API，不需要CSRF保护
            .csrf().disable()
            
            // 配置请求授权规则
            .authorizeRequests()
                // 允许访问健康检查端点（不需要认证）
                .antMatchers("/actuator/**").permitAll()
                
                // 允许访问Eureka的静态资源（CSS、JS等）
                .antMatchers("/eureka/css/**", "/eureka/js/**", "/eureka/fonts/**").permitAll()
                
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            .and()
            
            // 启用HTTP Basic认证
            .httpBasic();
    }

    /**
     * 配置用户认证信息
     * 这里使用内存方式存储用户信息
     * 生产环境建议使用数据库或LDAP
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .passwordEncoder(passwordEncoder())
            
            // 管理员账号（拥有所有权限）
            .withUser("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN")
            
            .and()
            
            // 普通服务账号（用于服务注册和发现）
            .withUser("eureka")
            .password(passwordEncoder().encode("eureka123"))
            .roles("USER");
    }

    /**
     * 密码编码器
     * 使用BCrypt加密算法
     * 
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
