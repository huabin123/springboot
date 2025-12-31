package com.huabin.transaction.controller;

import com.huabin.transaction.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 事务演示Controller
 *
 * @author huabin
 */
@Slf4j
@RestController
@RequestMapping("/transaction")
public class TransactionDemoController {

    @Autowired
    private RowLockService rowLockService;

    @Autowired
    private GapLockService gapLockService;

    @Autowired
    private MvccService mvccService;

    @Autowired
    private TransactionPropagationService propagationService;

    @Autowired
    private TransactionFailureService failureService;

    /**
     * 演示排他锁
     */
    @PostMapping("/row-lock/exclusive")
    public String exclusiveLock(@RequestParam Long fromId,
                                @RequestParam Long toId,
                                @RequestParam BigDecimal amount) {
        try {
            rowLockService.transferWithExclusiveLock(fromId, toId, amount);
            return "转账成功";
        } catch (Exception e) {
            log.error("转账失败", e);
            return "转账失败：" + e.getMessage();
        }
    }

    /**
     * 演示共享锁
     */
    @GetMapping("/row-lock/share/{accountId}")
    public String shareLock(@PathVariable Long accountId) {
        try {
            BigDecimal balance = rowLockService.queryBalanceWithShareLock(accountId);
            return "余额：" + balance;
        } catch (Exception e) {
            log.error("查询失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 演示乐观锁
     */
    @PutMapping("/row-lock/optimistic")
    public String optimisticLock(@RequestParam Long accountId,
                                  @RequestParam BigDecimal balance) {
        try {
            rowLockService.updateBalanceWithOptimisticLock(accountId, balance);
            return "更新成功";
        } catch (Exception e) {
            log.error("更新失败", e);
            return "更新失败：" + e.getMessage();
        }
    }

    /**
     * 演示间隙锁
     */
    @GetMapping("/gap-lock/query")
    public String gapLock(@RequestParam Integer minAge, @RequestParam Integer maxAge) {
        try {
            gapLockService.queryWithGapLock(minAge, maxAge);
            return "查询成功";
        } catch (Exception e) {
            log.error("查询失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 演示在间隙中插入数据
     */
    @PostMapping("/gap-lock/insert")
    public String insertIntoGap(@RequestParam Integer age,
                                 @RequestParam String name,
                                 @RequestParam String email) {
        try {
            gapLockService.insertIntoGap(age, name, email);
            return "插入成功";
        } catch (Exception e) {
            log.error("插入失败", e);
            return "插入失败：" + e.getMessage();
        }
    }

    /**
     * 演示MVCC - READ COMMITTED
     */
    @GetMapping("/mvcc/read-committed/{accountId}")
    public String readCommitted(@PathVariable Long accountId) {
        try {
            mvccService.readCommittedDemo(accountId);
            return "READ COMMITTED演示完成";
        } catch (Exception e) {
            log.error("演示失败", e);
            return "演示失败：" + e.getMessage();
        }
    }

    /**
     * 演示MVCC - REPEATABLE READ
     */
    @GetMapping("/mvcc/repeatable-read/{accountId}")
    public String repeatableRead(@PathVariable Long accountId) {
        try {
            mvccService.repeatableReadDemo(accountId);
            return "REPEATABLE READ演示完成";
        } catch (Exception e) {
            log.error("演示失败", e);
            return "演示失败：" + e.getMessage();
        }
    }

    /**
     * 演示当前读vs快照读
     */
    @GetMapping("/mvcc/current-vs-snapshot/{accountId}")
    public String currentVsSnapshot(@PathVariable Long accountId) {
        try {
            mvccService.currentReadVsSnapshotRead(accountId);
            return "当前读vs快照读演示完成";
        } catch (Exception e) {
            log.error("演示失败", e);
            return "演示失败：" + e.getMessage();
        }
    }

    /**
     * 演示REQUIRED传播行为
     */
    @PostMapping("/propagation/required")
    public String required() {
        try {
            propagationService.requiredDemo();
            return "REQUIRED演示成功";
        } catch (Exception e) {
            log.error("演示失败", e);
            return "演示失败：" + e.getMessage();
        }
    }

    /**
     * 演示REQUIRES_NEW传播行为
     */
    @PostMapping("/propagation/requires-new")
    public String requiresNew(@RequestParam(defaultValue = "false") boolean throwException) {
        try {
            propagationService.requiresNewDemo(throwException);
            return "REQUIRES_NEW演示成功";
        } catch (Exception e) {
            log.error("演示失败（预期行为）", e);
            return "外层事务回滚，但日志已保存：" + e.getMessage();
        }
    }

    /**
     * 演示NESTED传播行为
     */
    @PostMapping("/propagation/nested")
    public String nested(@RequestParam(defaultValue = "false") boolean innerException,
                         @RequestParam(defaultValue = "false") boolean outerException) {
        try {
            propagationService.nestedDemo(innerException, outerException);
            return "NESTED演示成功";
        } catch (Exception e) {
            log.error("演示失败", e);
            return "演示失败：" + e.getMessage();
        }
    }

    /**
     * 演示事务失效场景1：自调用
     */
    @PostMapping("/failure/self-invocation")
    public String selfInvocation() {
        try {
            failureService.selfInvocationFailure();
            return "不应该到这里";
        } catch (Exception e) {
            log.error("演示失败（预期行为）", e);
            return "自调用导致事务失效：" + e.getMessage();
        }
    }

    /**
     * 演示事务失效场景2：异常被捕获
     */
    @PostMapping("/failure/exception-caught")
    public String exceptionCaught() {
        try {
            failureService.exceptionCaughtFailure();
            return "异常被捕获，事务未回滚";
        } catch (Exception e) {
            return "异常：" + e.getMessage();
        }
    }

    /**
     * 演示正确的事务使用
     */
    @PostMapping("/correct-usage")
    public String correctUsage() {
        try {
            failureService.correctTransactionUsage();
            return "事务执行成功";
        } catch (Exception e) {
            log.error("事务执行失败", e);
            return "事务执行失败：" + e.getMessage();
        }
    }
}
