package com.huabin.redis.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * Redis基础操作Controller
 * 
 * @author huabin
 */
@RestController
@RequestMapping("/redis")
public class RedisController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置值
     */
    @PostMapping("/set")
    public String set(@RequestParam String key, @RequestParam String value) {
        redisTemplate.opsForValue().set(key, value);
        return "success";
    }

    /**
     * 设置值（带过期时间）
     */
    @PostMapping("/setex")
    public String setex(@RequestParam String key, 
                       @RequestParam String value, 
                       @RequestParam long seconds) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
        return "success";
    }

    /**
     * 获取值
     */
    @GetMapping("/get")
    public Object get(@RequestParam String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除key
     */
    @DeleteMapping("/delete")
    public Boolean delete(@RequestParam String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 判断key是否存在
     */
    @GetMapping("/exists")
    public Boolean exists(@RequestParam String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置过期时间
     */
    @PostMapping("/expire")
    public Boolean expire(@RequestParam String key, @RequestParam long seconds) {
        return redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    /**
     * 获取过期时间
     */
    @GetMapping("/ttl")
    public Long ttl(@RequestParam String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 自增
     */
    @PostMapping("/incr")
    public Long incr(@RequestParam String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 自减
     */
    @PostMapping("/decr")
    public Long decr(@RequestParam String key) {
        return redisTemplate.opsForValue().decrement(key);
    }
}
