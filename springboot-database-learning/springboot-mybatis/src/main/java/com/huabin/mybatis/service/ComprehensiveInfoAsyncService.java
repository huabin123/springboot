package com.huabin.mybatis.service;

import com.huabin.mybatis.entity.ComprehensiveInfo;
import com.huabin.mybatis.mapper.ComprehensiveInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 异步Service示例 - 演示如何在异步方法中正确使用Mapper
 * 
 * 关键点：
 * 1. Mapper注入方式与同步Service完全相同
 * 2. 异步方法使用@Async注解
 * 3. 异步方法应该返回void、Future或CompletableFuture
 * 4. 异步方法中的事务需要特殊处理
 * 5. 避免在同一个类中调用@Async方法（会失效）
 */
@Service
public class ComprehensiveInfoAsyncService {

    /**
     * Mapper注入 - 与同步Service完全相同
     * 
     * 重要：
     * - Spring管理的Mapper是线程安全的
     * - 可以在异步方法中直接使用
     * - 不需要任何特殊处理
     */
    @Autowired
    private ComprehensiveInfoMapper comprehensiveInfoMapper;

    /**
     * 事务模板 - 用于在异步方法中手动管理事务
     * 
     * 原因：
     * - @Transactional在异步方法中不生效
     * - 异步线程无法继承原线程的事务上下文
     * - 需要在异步方法内部重新开启事务
     */
    @Autowired
    private TransactionTemplate transactionTemplate;

    // ==================== 查询操作（无需事务） ====================

    /**
     * 异步查询单条记录
     * 
     * 使用场景：
     * - 查询耗时较长
     * - 不阻塞主线程
     * - 需要返回结果
     * 
     * @param prodCode 产品代码
     * @return CompletableFuture包装的查询结果
     */
    @Async("taskExecutor")  // 指定使用的线程池
    public CompletableFuture<ComprehensiveInfo> getByIdAsync(String prodCode) {
        System.out.println("异步查询开始 - 线程: " + Thread.currentThread().getName());
        
        // ✅ 直接使用Mapper，不需要特殊处理
        ComprehensiveInfo info = comprehensiveInfoMapper.selectByPrimaryKey(prodCode);
        
        System.out.println("异步查询完成 - 线程: " + Thread.currentThread().getName());
        return CompletableFuture.completedFuture(info);
    }

    /**
     * 异步查询所有记录
     * 
     * 使用场景：
     * - 数据量大，查询耗时
     * - 后台任务
     */
    @Async("taskExecutor")
    public CompletableFuture<List<ComprehensiveInfo>> getAllAsync() {
        System.out.println("异步查询所有 - 线程: " + Thread.currentThread().getName());
        
        List<ComprehensiveInfo> list = comprehensiveInfoMapper.selectAll();
        
        System.out.println("查询到 " + list.size() + " 条记录");
        return CompletableFuture.completedFuture(list);
    }

    // ==================== 写操作（需要事务） ====================

    /**
     * 异步新增 - 带事务
     * 
     * 重要：
     * - 使用TransactionTemplate手动管理事务
     * - 不能使用@Transactional（在异步方法中不生效）
     * 
     * @param info 要新增的对象
     * @return CompletableFuture<Integer> 影响的行数
     */
    @Async("taskExecutor")
    public CompletableFuture<Integer> addAsync(ComprehensiveInfo info) {
        System.out.println("异步新增开始 - 线程: " + Thread.currentThread().getName());
        
        // 使用事务模板执行带事务的操作
        Integer result = transactionTemplate.execute(status -> {
            try {
                int rows = comprehensiveInfoMapper.insert(info);
                System.out.println("新增成功，影响行数: " + rows);
                return rows;
            } catch (Exception e) {
                // 发生异常时回滚事务
                status.setRollbackOnly();
                System.err.println("新增失败，事务回滚: " + e.getMessage());
                throw e;
            }
        });
        
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 异步更新 - 带事务
     */
    @Async("taskExecutor")
    public CompletableFuture<Integer> updateAsync(ComprehensiveInfo info) {
        System.out.println("异步更新开始 - 线程: " + Thread.currentThread().getName());
        
        Integer result = transactionTemplate.execute(status -> {
            try {
                int rows = comprehensiveInfoMapper.updateByPrimaryKey(info);
                System.out.println("更新成功，影响行数: " + rows);
                return rows;
            } catch (Exception e) {
                status.setRollbackOnly();
                System.err.println("更新失败，事务回滚: " + e.getMessage());
                throw e;
            }
        });
        
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 异步删除 - 带事务
     */
    @Async("taskExecutor")
    public CompletableFuture<Integer> deleteAsync(String prodCode) {
        System.out.println("异步删除开始 - 线程: " + Thread.currentThread().getName());
        
        Integer result = transactionTemplate.execute(status -> {
            try {
                int rows = comprehensiveInfoMapper.deleteByPrimaryKey(prodCode);
                System.out.println("删除成功，影响行数: " + rows);
                return rows;
            } catch (Exception e) {
                status.setRollbackOnly();
                System.err.println("删除失败，事务回滚: " + e.getMessage());
                throw e;
            }
        });
        
        return CompletableFuture.completedFuture(result);
    }

    // ==================== 批量操作 ====================

    /**
     * 异步批量处理
     * 
     * 使用场景：
     * - 批量数据处理
     * - 后台任务
     * - 不需要立即返回结果
     */
    @Async("taskExecutor")
    public void batchProcessAsync(List<String> prodCodes) {
        System.out.println("批量处理开始 - 线程: " + Thread.currentThread().getName());
        System.out.println("待处理数量: " + prodCodes.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (String prodCode : prodCodes) {
            try {
                // 每条记录使用独立事务
                transactionTemplate.execute(status -> {
                    try {
                        ComprehensiveInfo info = comprehensiveInfoMapper.selectByPrimaryKey(prodCode);
                        if (info != null) {
                            // 处理逻辑
                            comprehensiveInfoMapper.updateByPrimaryKey(info);
                        }
                        return null;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw e;
                    }
                });
                successCount++;
            } catch (Exception e) {
                failCount++;
                System.err.println("处理失败: " + prodCode + ", 原因: " + e.getMessage());
            }
        }
        
        System.out.println("批量处理完成 - 成功: " + successCount + ", 失败: " + failCount);
    }

    // ==================== 组合异步操作 ====================

    /**
     * 并行查询多条记录
     * 
     * 使用场景：
     * - 需要查询多个不相关的数据
     * - 可以并行执行提高性能
     */
    public CompletableFuture<List<ComprehensiveInfo>> getMultipleAsync(List<String> prodCodes) {
        // 创建多个异步任务
        List<CompletableFuture<ComprehensiveInfo>> futures = prodCodes.stream()
                .map(this::getByIdAsync)
                .toList();
        
        // 等待所有任务完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(info -> info != null)
                        .toList());
    }

    // ==================== 无返回值的异步方法 ====================

    /**
     * 异步处理 - 无返回值
     * 
     * 使用场景：
     * - 不需要返回结果
     * - 触发即忘（Fire and Forget）
     * - 后台任务、日志记录等
     * 
     * 注意：
     * - 异常会被AsyncUncaughtExceptionHandler捕获
     * - 调用方无法获取异常信息
     */
    @Async("taskExecutor")
    public void processInBackgroundAsync(String prodCode) {
        System.out.println("后台处理开始 - 线程: " + Thread.currentThread().getName());
        
        try {
            ComprehensiveInfo info = comprehensiveInfoMapper.selectByPrimaryKey(prodCode);
            if (info != null) {
                // 执行一些后台处理逻辑
                System.out.println("处理: " + info);
            }
        } catch (Exception e) {
            // 异常会被AsyncUncaughtExceptionHandler捕获
            System.err.println("后台处理失败: " + e.getMessage());
            throw e;
        }
        
        System.out.println("后台处理完成");
    }

    // ==================== 错误示例（仅供参考，不要使用） ====================

    /**
     * ❌ 错误示例1：在同一个类中调用异步方法
     * 
     * 问题：this.getByIdAsync() 不会异步执行
     * 原因：Spring AOP代理机制，同类内部调用不经过代理
     * 
     * 解决方案：
     * 1. 将异步方法拆分到不同的Service
     * 2. 自己注入自己（@Autowired @Lazy private ComprehensiveInfoAsyncService self）
     */
    // public void wrongUsage1(String prodCode) {
    //     this.getByIdAsync(prodCode);  // ❌ 不会异步执行！
    // }

    /**
     * ❌ 错误示例2：异步方法使用@Transactional
     * 
     * 问题：事务不会生效
     * 原因：异步线程无法继承原线程的事务上下文
     * 
     * 解决方案：使用TransactionTemplate
     */
    // @Async
    // @Transactional  // ❌ 不会生效！
    // public void wrongUsage2(ComprehensiveInfo info) {
    //     comprehensiveInfoMapper.insert(info);
    // }

    /**
     * ❌ 错误示例3：异步方法返回普通对象
     * 
     * 问题：返回值会丢失
     * 原因：异步方法在另一个线程执行，调用方无法获取返回值
     * 
     * 解决方案：返回CompletableFuture、Future或void
     */
    // @Async
    // public ComprehensiveInfo wrongUsage3(String prodCode) {  // ❌ 返回值会丢失！
    //     return comprehensiveInfoMapper.selectByPrimaryKey(prodCode);
    // }
}
