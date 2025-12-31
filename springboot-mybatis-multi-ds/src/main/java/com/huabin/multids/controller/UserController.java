package com.huabin.multids.controller;

import com.huabin.multids.db1.entity.User;
import com.huabin.multids.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 用户控制器（主数据源 springboot_db）
 * 
 * API接口：
 * GET    /api/users/{id}        - 根据ID查询用户
 * GET    /api/users             - 查询所有用户
 * GET    /api/users/search      - 根据条件查询用户
 * POST   /api/users             - 创建用户
 * PUT    /api/users             - 更新用户
 * DELETE /api/users/{id}        - 删除用户
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 根据ID查询用户
     * 
     * 测试命令：
     * curl http://localhost:8080/api/users/1
     */
    @GetMapping("/{id}")
    public Map<String, Object> getUserById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = userService.getUserById(id);
            if (user != null) {
                result.put("success", true);
                result.put("data", user);
                result.put("message", "查询成功");
            } else {
                result.put("success", false);
                result.put("message", "用户不存在");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 查询所有用户
     * 
     * 测试命令：
     * curl http://localhost:8080/api/users
     */
    @GetMapping
    public Map<String, Object> getAllUsers() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<User> users = userService.getAllUsers();
            result.put("success", true);
            result.put("data", users);
            result.put("count", users.size());
            result.put("message", "查询成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 根据条件查询用户
     * 
     * 测试命令：
     * curl "http://localhost:8080/api/users/search?username=test&status=1"
     */
    @GetMapping("/search")
    public Map<String, Object> searchUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<User> users = userService.getUsersByCondition(username, status);
            result.put("success", true);
            result.put("data", users);
            result.put("count", users.size());
            result.put("message", "查询成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 创建用户
     * 
     * 测试命令：
     * curl -X POST http://localhost:8080/api/users \
     *   -H "Content-Type: application/json" \
     *   -d '{"username":"test","password":"123456","email":"test@example.com","phone":"13800138000","status":1}'
     */
    @PostMapping
    public Map<String, Object> createUser(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = userService.createUser(user);
            if (rows > 0) {
                result.put("success", true);
                result.put("data", user);
                result.put("message", "创建成功");
            } else {
                result.put("success", false);
                result.put("message", "创建失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 更新用户
     * 
     * 测试命令：
     * curl -X PUT http://localhost:8080/api/users \
     *   -H "Content-Type: application/json" \
     *   -d '{"id":1,"username":"test2","password":"123456","email":"test2@example.com","phone":"13800138000","status":1}'
     */
    @PutMapping
    public Map<String, Object> updateUser(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = userService.updateUser(user);
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "更新成功");
            } else {
                result.put("success", false);
                result.put("message", "更新失败，用户不存在");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 删除用户
     * 
     * 测试命令：
     * curl -X DELETE http://localhost:8080/api/users/1
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = userService.deleteUser(id);
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "删除成功");
            } else {
                result.put("success", false);
                result.put("message", "删除失败，用户不存在");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 健康检查
     * 
     * 测试命令：
     * curl http://localhost:8080/api/users/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "用户服务正常（主数据源 springboot_db）");
        result.put("datasource", "primary");
        result.put("database", "springboot_db");
        return result;
    }
}
