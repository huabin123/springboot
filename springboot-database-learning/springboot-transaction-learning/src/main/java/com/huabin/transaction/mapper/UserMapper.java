package com.huabin.transaction.mapper;

import com.huabin.transaction.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper
 * 
 * @author huabin
 */
public interface UserMapper {
    
    /**
     * 根据年龄范围查询用户（演示间隙锁）
     */
    List<User> selectByAgeRange(@Param("minAge") Integer minAge, 
                                 @Param("maxAge") Integer maxAge);
    
    /**
     * 根据年龄范围查询用户并加锁
     */
    List<User> selectByAgeRangeForUpdate(@Param("minAge") Integer minAge, 
                                          @Param("maxAge") Integer maxAge);
    
    /**
     * 插入用户
     */
    int insert(User user);
    
    /**
     * 根据ID查询
     */
    User selectById(@Param("id") Long id);
}
