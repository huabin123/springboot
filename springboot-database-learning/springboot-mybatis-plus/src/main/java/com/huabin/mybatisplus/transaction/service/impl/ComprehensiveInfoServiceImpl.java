package com.huabin.mybatisplus.transaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huabin.mybatisplus.transaction.entity.ComprehensiveInfo;
import com.huabin.mybatisplus.transaction.mapper.ComprehensiveInfoMapper;
import com.huabin.mybatisplus.transaction.service.IComprehensiveInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author huabin
 * @since 2023-06-02
 */
@Service
public class ComprehensiveInfoServiceImpl extends ServiceImpl<ComprehensiveInfoMapper, ComprehensiveInfo> implements IComprehensiveInfoService {

    @Autowired
    ComprehensiveInfoMapper comprehensiveInfoMapper;

    @Override
    public List<ComprehensiveInfo> getComprehensiveInfo() {

        QueryWrapper<ComprehensiveInfo> queryWrapper = Wrappers.query();
        queryWrapper.like("prod_name", "产品");

        return comprehensiveInfoMapper.selectList(queryWrapper);
    }
}
