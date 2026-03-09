package com.huabin.dict.controller;

import com.huabin.dict.service.SysDictTypeService;
import com.huabin.dict.vo.SysDictTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典类型控制器
 */
@RestController
@RequestMapping("/dict")
public class SysDictTypeController {

    @Autowired
    private SysDictTypeService sysDictTypeService;

    /**
     * 根据父字典ID查询字典类型及其字典项列表
     * 
     * @param dictPid 父字典ID
     * @return 字典类型VO列表，包含字典项
     */
    @GetMapping("/types")
    public List<SysDictTypeVO> getDictTypeWithItems(@RequestParam("dictPid") Long dictPid) {
        return sysDictTypeService.getDictTypeWithItemsByPid(dictPid);
    }
}
