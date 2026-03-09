package com.huabin.dict.mapper;

import com.huabin.dict.vo.SysDictTypeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 字典类型Mapper接口
 */
@Mapper
public interface SysDictTypeMapper {

    /**
     * 根据父字典ID查询字典类型及其字典项列表
     * 
     * @param dictPid 父字典ID
     * @return 字典类型VO列表，包含字典项
     */
    List<SysDictTypeVO> selectDictTypeWithItemsByPid(@Param("dictPid") Long dictPid);
}
