package com.huabin.multids.db1.mapper;

import com.huabin.multids.db1.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 用户Mapper接口（主数据源 springboot_db）
 * 
 * 说明：
 * - 此接口为示例代码，实际使用时应通过 MyBatis Generator 生成
 * - 对应XML文件：src/main/resources/mapper/db1/UserMapper.xml
 * - 数据源配置：PrimaryDataSourceConfig
 */
public interface UserMapper {

    /**
     * 根据主键删除
     */
    int deleteByPrimaryKey(Long id);

    /**
     * 插入记录
     */
    int insert(User record);

    /**
     * 根据主键查询
     */
    User selectByPrimaryKey(Long id);

    /**
     * 查询所有用户
     */
    List<User> selectAll();

    /**
     * 根据用户名查询
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据主键更新
     */
    int updateByPrimaryKey(User record);

    /**
     * 根据条件查询用户列表
     */
    List<User> selectByCondition(@Param("username") String username, 
                                  @Param("status") Integer status);
}
