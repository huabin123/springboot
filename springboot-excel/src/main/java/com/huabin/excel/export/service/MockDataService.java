package com.huabin.excel.export.service;

import com.huabin.excel.export.model.HeaderMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @Author huabin
 * @DateTime 2025-12-30
 * @Desc 模拟数据服务
 * 
 * 职责：
 * 1. 模拟从数据库查询数据（返回英文字段名）
 * 2. 使用 LinkedHashMap 保证字段顺序
 * 3. 支持生成大量测试数据
 * 4. 提供表头映射配置（英文 -> 中文）
 * 
 * 设计说明：
 * - 数据库查询返回英文字段名（模拟真实场景）
 * - 使用 HeaderMapping 定义英文到中文的映射关系
 * - LinkedHashMap 保证字段顺序与插入顺序一致
 * - 支持自定义数据量
 */
@Service
public class MockDataService {

    private static final Logger logger = LoggerFactory.getLogger(MockDataService.class);

    /**
     * 模拟从数据库查询数据（返回英文字段名）
     * 
     * @param count 数据条数
     * @return 数据列表（字段名为英文）
     */
    public List<LinkedHashMap<String, Object>> queryData(int count) {
        logger.info("开始模拟查询数据，数据量: {}", count);
        
        long startTime = System.currentTimeMillis();
        List<LinkedHashMap<String, Object>> dataList = new ArrayList<>(count);
        
        // 模拟数据生成（使用英文字段名，模拟真实数据库查询结果）
        for (int i = 1; i <= count; i++) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            
            // 使用英文字段名（模拟数据库字段）
            row.put("orderNo", "ORD" + String.format("%010d", i));
            row.put("customerName", generateCustomerName(i));
            row.put("phone", generatePhone(i));
            row.put("amount", generateAmount(i));
            row.put("status", generateStatus(i));
            row.put("orderTime", generateDateTime(i));
            row.put("address", generateAddress(i));
            row.put("productName", generateProductName(i));
            row.put("quantity", generateQuantity(i));
            row.put("remark", generateRemark(i));
            
            dataList.add(row);
            
            // 每10000条打印一次进度
            if (i % 10000 == 0) {
                logger.info("数据生成进度: {}/{}", i, count);
            }
        }
        
        long endTime = System.currentTimeMillis();
        logger.info("数据查询完成，数据量: {}, 耗时: {}ms", count, (endTime - startTime));
        
        return dataList;
    }

    /**
     * 获取表头映射配置
     * 
     * 定义数据库字段（英文）到 Excel 表头（中文）的映射关系
     * 列表顺序决定了 Excel 列的顺序
     * 
     * @return 表头映射列表
     */
    public List<HeaderMapping> getHeaderMappings() {
        List<HeaderMapping> mappings = new ArrayList<>();
        
        // 按照期望的 Excel 列顺序添加映射
        mappings.add(new HeaderMapping("orderNo", "订单编号"));
        mappings.add(new HeaderMapping("customerName", "客户姓名"));
        mappings.add(new HeaderMapping("phone", "联系电话"));
        mappings.add(new HeaderMapping("amount", "订单金额"));
        mappings.add(new HeaderMapping("status", "订单状态"));
        mappings.add(new HeaderMapping("orderTime", "下单时间"));
        mappings.add(new HeaderMapping("address", "收货地址"));
        mappings.add(new HeaderMapping("productName", "商品名称"));
        mappings.add(new HeaderMapping("quantity", "商品数量"));
        mappings.add(new HeaderMapping("remark", "备注信息"));
        
        return mappings;
    }

    /**
     * 生成客户姓名
     */
    private String generateCustomerName(int index) {
        String[] surnames = {"张", "王", "李", "赵", "刘", "陈", "杨", "黄", "周", "吴"};
        String[] names = {"伟", "芳", "娜", "秀英", "敏", "静", "丽", "强", "磊", "军"};
        
        int surnameIndex = index % surnames.length;
        int nameIndex = (index / surnames.length) % names.length;
        
        return surnames[surnameIndex] + names[nameIndex];
    }

    /**
     * 生成联系电话
     */
    private String generatePhone(int index) {
        long phone = 13000000000L + (index % 900000000);
        return String.valueOf(phone);
    }

    /**
     * 生成订单金额
     */
    private BigDecimal generateAmount(int index) {
        double amount = 10.0 + (index % 10000) * 0.99;
        return new BigDecimal(String.format("%.2f", amount));
    }

    /**
     * 生成订单状态
     */
    private String generateStatus(int index) {
        String[] statuses = {"待支付", "已支付", "配送中", "已完成", "已取消"};
        return statuses[index % statuses.length];
    }

    /**
     * 生成下单时间
     */
    private String generateDateTime(int index) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long timestamp = System.currentTimeMillis() - (long) index * 60000; // 每条数据间隔1分钟
        return sdf.format(new Date(timestamp));
    }

    /**
     * 生成收货地址
     */
    private String generateAddress(int index) {
        String[] provinces = {"北京市", "上海市", "广东省", "浙江省", "江苏省"};
        String[] cities = {"朝阳区", "浦东新区", "天河区", "西湖区", "玄武区"};
        String[] streets = {"中山路", "人民路", "解放路", "建设路", "和平路"};
        
        int provinceIndex = index % provinces.length;
        int cityIndex = (index / provinces.length) % cities.length;
        int streetIndex = (index / (provinces.length * cities.length)) % streets.length;
        
        return provinces[provinceIndex] + cities[cityIndex] + streets[streetIndex] + (index % 1000) + "号";
    }

    /**
     * 生成商品名称
     */
    private String generateProductName(int index) {
        String[] categories = {"电子产品", "服装鞋帽", "食品饮料", "家居用品", "图书音像"};
        String[] products = {"iPhone", "T恤", "矿泉水", "沙发", "小说"};
        
        int categoryIndex = index % categories.length;
        int productIndex = (index / categories.length) % products.length;
        
        return categories[categoryIndex] + "-" + products[productIndex];
    }

    /**
     * 生成商品数量
     */
    private Integer generateQuantity(int index) {
        return (index % 10) + 1;
    }

    /**
     * 生成备注信息
     */
    private String generateRemark(int index) {
        if (index % 5 == 0) {
            return "请尽快发货，谢谢！";
        } else if (index % 3 == 0) {
            return "周末配送";
        } else {
            return "";
        }
    }

    /**
     * 获取表头（从第一条数据中提取）
     * 
     * @param dataList 数据列表
     * @return 表头列表
     */
    public List<String> getHeaders(List<LinkedHashMap<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return new ArrayList<>();
        }
        
        // LinkedHashMap 保证了顺序，直接获取 keySet
        return new ArrayList<>(dataList.get(0).keySet());
    }
}
