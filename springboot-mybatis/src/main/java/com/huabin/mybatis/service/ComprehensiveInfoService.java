package com.huabin.mybatis.service;

import com.huabin.mybatis.entity.ComprehensiveInfo;
import com.huabin.mybatis.mapper.ComprehensiveInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc ComprehensiveInfo业务逻辑层
 * 
 * Mapper注入示例：
 * 1. 使用@Autowired注解自动注入Mapper
 * 2. 确保启动类添加了@MapperScan或Mapper接口添加了@Mapper注解
 * 3. 如果注入失败，参考 MAPPER_INJECTION_TROUBLESHOOTING.md 排查
 */
@Service
public class ComprehensiveInfoService {

    /**
     * 方式1：字段注入（最常用）
     * 优点：代码简洁
     * 缺点：不利于单元测试，可能出现循环依赖
     */
    @Autowired
    private ComprehensiveInfoMapper comprehensiveInfoMapper;

    /**
     * 方式2：构造器注入（推荐）
     * 优点：
     * 1. 依赖关系明确，便于单元测试
     * 2. 可以使用final修饰，保证不可变性
     * 3. Spring 4.3+版本可以省略@Autowired
     * 4. 避免循环依赖问题
     */
    // private final ComprehensiveInfoMapper comprehensiveInfoMapper;
    // 
    // public ComprehensiveInfoService(ComprehensiveInfoMapper comprehensiveInfoMapper) {
    //     this.comprehensiveInfoMapper = comprehensiveInfoMapper;
    // }

    /**
     * 方式3：Setter注入（不推荐）
     * 优点：可选依赖时使用
     * 缺点：依赖关系不明确
     */
    // private ComprehensiveInfoMapper comprehensiveInfoMapper;
    // 
    // @Autowired
    // public void setComprehensiveInfoMapper(ComprehensiveInfoMapper comprehensiveInfoMapper) {
    //     this.comprehensiveInfoMapper = comprehensiveInfoMapper;
    // }

    /**
     * 根据主键查询
     */
    public ComprehensiveInfo getById(String prodCode) {
        return comprehensiveInfoMapper.selectByPrimaryKey(prodCode);
    }

    /**
     * 查询所有
     */
    public List<ComprehensiveInfo> getAll() {
        return comprehensiveInfoMapper.selectAll();
    }

    /**
     * 新增
     */
    public int add(ComprehensiveInfo info) {
        return comprehensiveInfoMapper.insert(info);
    }

    /**
     * 更新
     */
    public int update(ComprehensiveInfo info) {
        return comprehensiveInfoMapper.updateByPrimaryKey(info);
    }

    /**
     * 删除
     */
    public int delete(String prodCode) {
        return comprehensiveInfoMapper.deleteByPrimaryKey(prodCode);
    }
}
