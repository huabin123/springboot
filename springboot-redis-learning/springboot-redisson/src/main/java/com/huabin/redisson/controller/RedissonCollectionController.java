package com.huabin.redisson.controller;

import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redisson分布式集合Controller
 * 
 * @author huabin
 */
@RestController
@RequestMapping("/collection")
public class RedissonCollectionController {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 分布式Map操作
     */
    @PostMapping("/map/put")
    public String mapPut(@RequestParam String mapKey, 
                        @RequestParam String key, 
                        @RequestParam String value) {
        RMap<String, String> map = redissonClient.getMap(mapKey);
        map.put(key, value);
        return "success";
    }

    @GetMapping("/map/get")
    public Object mapGet(@RequestParam String mapKey, @RequestParam String key) {
        RMap<String, String> map = redissonClient.getMap(mapKey);
        return map.get(key);
    }

    @GetMapping("/map/all")
    public Map<String, String> mapGetAll(@RequestParam String mapKey) {
        RMap<String, String> map = redissonClient.getMap(mapKey);
        return map.readAllMap();
    }

    /**
     * 分布式Set操作
     */
    @PostMapping("/set/add")
    public String setAdd(@RequestParam String setKey, @RequestParam String value) {
        RSet<String> set = redissonClient.getSet(setKey);
        set.add(value);
        return "success";
    }

    @GetMapping("/set/all")
    public Set<String> setGetAll(@RequestParam String setKey) {
        RSet<String> set = redissonClient.getSet(setKey);
        return set.readAll();
    }

    @GetMapping("/set/contains")
    public Boolean setContains(@RequestParam String setKey, @RequestParam String value) {
        RSet<String> set = redissonClient.getSet(setKey);
        return set.contains(value);
    }

    /**
     * 分布式List操作
     */
    @PostMapping("/list/add")
    public String listAdd(@RequestParam String listKey, @RequestParam String value) {
        RList<String> list = redissonClient.getList(listKey);
        list.add(value);
        return "success";
    }

    @GetMapping("/list/all")
    public List<String> listGetAll(@RequestParam String listKey) {
        RList<String> list = redissonClient.getList(listKey);
        return list.readAll();
    }

    @GetMapping("/list/get")
    public String listGet(@RequestParam String listKey, @RequestParam int index) {
        RList<String> list = redissonClient.getList(listKey);
        return list.get(index);
    }

    /**
     * 分布式Queue操作
     */
    @PostMapping("/queue/offer")
    public String queueOffer(@RequestParam String queueKey, @RequestParam String value) {
        RQueue<String> queue = redissonClient.getQueue(queueKey);
        queue.offer(value);
        return "success";
    }

    @GetMapping("/queue/poll")
    public String queuePoll(@RequestParam String queueKey) {
        RQueue<String> queue = redissonClient.getQueue(queueKey);
        return queue.poll();
    }

    @GetMapping("/queue/peek")
    public String queuePeek(@RequestParam String queueKey) {
        RQueue<String> queue = redissonClient.getQueue(queueKey);
        return queue.peek();
    }

    /**
     * 分布式Deque操作（双端队列）
     */
    @PostMapping("/deque/addFirst")
    public String dequeAddFirst(@RequestParam String dequeKey, @RequestParam String value) {
        RDeque<String> deque = redissonClient.getDeque(dequeKey);
        deque.addFirst(value);
        return "success";
    }

    @PostMapping("/deque/addLast")
    public String dequeAddLast(@RequestParam String dequeKey, @RequestParam String value) {
        RDeque<String> deque = redissonClient.getDeque(dequeKey);
        deque.addLast(value);
        return "success";
    }

    @GetMapping("/deque/pollFirst")
    public String dequePollFirst(@RequestParam String dequeKey) {
        RDeque<String> deque = redissonClient.getDeque(dequeKey);
        return deque.pollFirst();
    }

    @GetMapping("/deque/pollLast")
    public String dequePollLast(@RequestParam String dequeKey) {
        RDeque<String> deque = redissonClient.getDeque(dequeKey);
        return deque.pollLast();
    }

    /**
     * 分布式SortedSet操作（有序集合）
     */
    @PostMapping("/sortedset/add")
    public String sortedSetAdd(@RequestParam String setKey, 
                              @RequestParam String value, 
                              @RequestParam double score) {
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(setKey);
        sortedSet.add(score, value);
        return "success";
    }

    @GetMapping("/sortedset/range")
    public List<String> sortedSetRange(@RequestParam String setKey, 
                                       @RequestParam int start, 
                                       @RequestParam int end) {
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(setKey);
        return sortedSet.valueRange(start, end).stream().toList();
    }

    @GetMapping("/sortedset/rank")
    public Integer sortedSetRank(@RequestParam String setKey, @RequestParam String value) {
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(setKey);
        return sortedSet.rank(value);
    }
}
