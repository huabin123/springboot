package com.huabin.excel;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;

import java.util.Collections;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

/**
 * 添加合并区Handler
 */
public class AddCellRangeWriteHandler implements SheetWriteHandler {

    private final List<CellRangeAddress> rangeCellList;

    public AddCellRangeWriteHandler(List<CellRangeAddress> rangeCellList) {
        this.rangeCellList = (rangeCellList == null) ? Collections.emptyList() : rangeCellList;
    }

    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        Sheet sheet = writeSheetHolder.getSheet();
        for (CellRangeAddress cellRangeAddress : this.rangeCellList) {
            sheet.addMergedRegionUnsafe(cellRangeAddress);
        }
    }
}

