package com.huabin.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * @Author huabin
 * @DateTime 2024-08-15 17:26
 * @Desc
 */
public class MergeCell {

//    public static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(4);

    public static void repeatedWrite() {
//        // 并发分页查询数据
//        int count = count();
//        int pageSize = 1000;
//        int pageCount = count / pageSize;
//        pageCount = pageCount * pageSize < count ? pageCount + 1 : pageCount;
//        List<Future<List<BillExpenseDetail>>> futureList = new ArrayList<>(pageCount);
//        for (int i = 0; i < pageCount; i++) {
//            int index = i;
//            Future<List<BillExpenseDetail>> submit = EXECUTOR_SERVICE.submit(
//                    () -> pageQuery(index * pageSize, pageSize));
//            futureList.add(submit);
//        }

        List<BillExpenseDetail> details = new ArrayList<>();
        List<List<BillExpenseDetail>> allDetails = new ArrayList<>();
        for (int i = 0; i < 200000; i++) {
            details.add(BillExpenseDetail.builder()
                    .amount(BigDecimal.valueOf(5780))
                    .price("1")
                    .createDate(new Date())
                    .direction("收")
                    .number(String.valueOf(i/100))
                    .quantity("8")
                    .subject("西药费")
                    .subject0("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject1("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject2("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject3("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject4("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject5("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject6("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject7("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject8("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject9("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .totalAmount(BigDecimal.valueOf(10000))
                    .unit("盒")
                    .build()
            );
        }
        allDetails.add(details);
        for (int i = 200000; i < 500000; i++) {
            details.add(BillExpenseDetail.builder()
                    .amount(BigDecimal.valueOf(5780))
                    .price("2")
                    .createDate(new Date())
                    .direction("支")
                    .number(String.valueOf(i / 100))
                    .quantity("8")
                    .subject("退费")
                    .subject0("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject1("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject2("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject3("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject4("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject5("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject6("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject7("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject8("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .subject9("如果你的需求更复杂，可以自定义一个合并策略类。例如，LoopMergeStrategy，根据你的需求定制合并规则。")
                    .totalAmount(BigDecimal.valueOf(10000))
                    .unit("次")
                    .build()
            );
        }
        allDetails.add(details);

        // 追加写
        String fileName = "/Users/huabin/workspace/playground/my-github/springboot/springboot-excel/src/main/resources/out2.xlsx";

        ExcelWriter excelWriter = EasyExcel.write(fileName, BillExpenseDetail.class).build();
        // 行计数，初始值取列头行数
        int lineCount = 1;
        // sheet中需要合并的列的索引
        final int[] mergeColumnIndex = {0, 1, 2, 3};
        WriteSheet writeSheet;
        for (List<BillExpenseDetail> detailList : allDetails) {
                List<CellRangeAddress> rangeCellList = createCellRange(detailList, mergeColumnIndex, lineCount);
                lineCount += detailList.size();
                // 写出到一个sheet页中，sheetName固定
                writeSheet = EasyExcel.writerSheet(0).registerWriteHandler(new AddCellRangeWriteHandler(rangeCellList)).build();
                excelWriter.write(detailList, writeSheet);
                // 及时释放内存
                detailList.clear();
        }
        excelWriter.finish();

    }

    /**
     * 生成合并区
     *
     * @param detailList       票据
     * @param mergeColumnIndex sheet 中需要合并的列的索引
     * @param lineCount        行计数（包括列头行）
     * @return 合并区
     */
    private static List<CellRangeAddress> createCellRange(List<BillExpenseDetail> detailList, int[] mergeColumnIndex, int lineCount) {
        if (detailList.isEmpty()) {
            return Collections.emptyList();
        }

        List<CellRangeAddress> rangeCellList = new ArrayList<>();
        Map<String, Long> groupMap = detailList.stream().collect(Collectors.groupingBy(BillExpenseDetail::getNumber, Collectors.counting()));
        for (Map.Entry<String, Long> entry : groupMap.entrySet()) {
            int count = entry.getValue().intValue();
            int startRowIndex = lineCount;
            // 如合并第2到4行，共3行，行索引从1到3
            int endRowIndex = lineCount + count - 1;
            for (int columnIndex : mergeColumnIndex) {
                rangeCellList.add(new CellRangeAddress(startRowIndex, endRowIndex, columnIndex, columnIndex));
            }
            lineCount += count;
        }
        return rangeCellList;
    }

    public static void main(String[] args) {
        repeatedWrite();
    }

}
