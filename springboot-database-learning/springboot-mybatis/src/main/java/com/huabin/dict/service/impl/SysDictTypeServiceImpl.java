package com.huabin.dict.service.impl;

import com.huabin.dict.mapper.SysDictTypeMapper;
import com.huabin.dict.service.SysDictTypeService;
import com.huabin.dict.vo.SysDictTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典类型服务实现类
 */
@Service
public class SysDictTypeServiceImpl implements SysDictTypeService {

    @Autowired
    private SysDictTypeMapper sysDictTypeMapper;

    @Override
    public List<SysDictTypeVO> getDictTypeWithItemsByPid(Long dictPid) {
        return sysDictTypeMapper.selectDictTypeWithItemsByPid(dictPid);
    }
}
