package com.huabin.dict;

import com.huabin.dict.entity.SysDictItem;
import com.huabin.dict.mapper.SysDictTypeMapper;
import com.huabin.dict.vo.SysDictTypeVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 字典类型Mapper测试类
 */
@SpringBootTest
public class SysDictTypeMapperTest {

    @Autowired
    private SysDictTypeMapper sysDictTypeMapper;

    /**
     * 测试查询顶级字典（dictPid = 0）
     */
    @Test
    public void testSelectTopLevelDictTypes() {
        System.out.println("========== 测试查询顶级字典（dictPid = 0） ==========");
        List<SysDictTypeVO> result = sysDictTypeMapper.selectDictTypeWithItemsByPid(0L);
        
        System.out.println("查询结果数量: " + result.size());
        for (SysDictTypeVO vo : result) {
            System.out.println("\n字典类型: " + vo.getDictName() + " (ID: " + vo.getDictId() + ")");
            System.out.println("字典项数量: " + (vo.getItems() != null ? vo.getItems().size() : 0));
            if (vo.getItems() != null) {
                vo.getItems().forEach(item -> 
                    System.out.println("  - " + item.getItemName() + ": " + item.getItemValue())
                );
            }
        }
    }

    /**
     * 测试查询子字典（dictPid = 1）
     */
    @Test
    public void testSelectChildDictTypes() {
        System.out.println("========== 测试查询子字典（dictPid = 1） ==========");
        List<SysDictTypeVO> result = sysDictTypeMapper.selectDictTypeWithItemsByPid(1L);
        
        System.out.println("查询结果数量: " + result.size());
        for (SysDictTypeVO vo : result) {
            System.out.println("\n字典类型: " + vo.getDictName() + " (ID: " + vo.getDictId() + ")");
            System.out.println("字典项数量: " + (vo.getItems() != null ? vo.getItems().size() : 0));
            if (vo.getItems() != null) {
                vo.getItems().forEach(item -> 
                    System.out.println("  - " + item.getItemName() + ": " + item.getItemValue())
                );
            }
        }
    }

    /**
     * 测试查询不存在的父字典
     */
    @Test
    public void testSelectNonExistentDictTypes() {
        System.out.println("========== 测试查询不存在的父字典（dictPid = 999） ==========");
        List<SysDictTypeVO> result = sysDictTypeMapper.selectDictTypeWithItemsByPid(999L);
        
        System.out.println("查询结果数量: " + result.size());
        System.out.println("结果应该为空列表");
    }

    /**
     * 测试Collection映射的正确性
     * 验证使用列别名后，Collection是否能正确聚合数据
     */
    @Test
    public void testCollectionMappingCorrectness() {
        System.out.println("========== 测试Collection映射正确性 ==========");
        List<SysDictTypeVO> result = sysDictTypeMapper.selectDictTypeWithItemsByPid(0L);
        
        // 验证查询结果不为空
        assertNotNull(result, "查询结果不应为null");
        assertTrue(result.size() > 0, "应该查询到至少一条字典类型");
        
        // 验证第一个字典类型（性别）
        SysDictTypeVO genderDict = result.stream()
                .filter(vo -> "性别".equals(vo.getDictName()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(genderDict, "应该能找到'性别'字典");
        assertEquals(1L, genderDict.getDictId(), "性别字典的ID应该是1");
        assertNotNull(genderDict.getItems(), "字典项列表不应为null");
        assertEquals(3, genderDict.getItems().size(), "性别字典应该有3个字典项");
        
        // 验证字典项的关联关系
        for (SysDictItem item : genderDict.getItems()) {
            assertNotNull(item.getItemId(), "字典项ID不应为null");
            assertEquals(genderDict.getDictId(), item.getDictId(), 
                "字典项的dictId应该等于字典类型的dictId");
            assertNotNull(item.getItemName(), "字典项名称不应为null");
            assertNotNull(item.getItemValue(), "字典项值不应为null");
            
            System.out.println(String.format(
                "验证通过: 字典项[%d] %s=%s, 关联字典ID=%d",
                item.getItemId(), item.getItemName(), item.getItemValue(), item.getDictId()
            ));
        }
        
        // 验证第二个字典类型（状态）
        SysDictTypeVO statusDict = result.stream()
                .filter(vo -> "状态".equals(vo.getDictName()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(statusDict, "应该能找到'状态'字典");
        assertEquals(2L, statusDict.getDictId(), "状态字典的ID应该是2");
        assertNotNull(statusDict.getItems(), "字典项列表不应为null");
        assertEquals(2, statusDict.getItems().size(), "状态字典应该有2个字典项");
        
        // 验证状态字典的关联关系
        for (SysDictItem item : statusDict.getItems()) {
            assertEquals(statusDict.getDictId(), item.getDictId(), 
                "字典项的dictId应该等于字典类型的dictId");
        }
        
        System.out.println("\n✅ Collection映射测试全部通过！");
        System.out.println("✅ 使用列别名(item_dict_id)不影响Collection的聚合功能");
        System.out.println("✅ 字典类型与字典项的关联关系正确");
    }
}
