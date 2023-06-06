package com.huabin.mybatisplus.transaction.service;

import com.huabin.mybatisplus.transaction.entity.ComprehensiveInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author huabin
 * @since 2023-06-02
 */
public interface IComprehensiveInfoService extends IService<ComprehensiveInfo> {

    List<ComprehensiveInfo> getComprehensiveInfo();
}
