package com.huabin.excel;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;

/**
 * @Author huabin
 * @DateTime 2024-06-03 16:12
 * @Desc
 */
public class WaterMarkTest {
    public static void main(String[] args) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook("/Users/huabin/workspace/playground/my-github/springboot/springboot-excel/src/main/resources/out.xlsx");
            FileOutputStream out = new FileOutputStream("/Users/huabin/workspace/playground/my-github/springboot/springboot-excel/src/main/resources/out5.xlsx");

            XSSFSheet sheet = workbook.getSheetAt(0);

//            sheet.setAutoFilter(CellRangeAddress.valueOf("A1:B200"));
            sheet.setAutoFilter(new CellRangeAddress(0,0,0,2));

            sheet.protectSheet(IdUtil.fastSimpleUUID());
            sheet.enableLocking();
            sheet.lockAutoFilter(false);
            sheet.lockSort(false);
//            sheet.lockSelectLockedCells(true);
//            sheet.lockSelectUnlockedCells(true);
//            workbook.getSheet("Sheet1");



            //add picture data to this workbook.
//                FileInputStream is = new FileInputStream("/Users/Tony/Downloads/data_image.png");
//            byte[] bytes = IOUtils.toByteArray(is);


            BufferedImage image = FontImage.createBufferedImage(null);
            // 导出到字节流B
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, "png", os);

            int pictureIdx = workbook.addPicture(os.toByteArray(), Workbook.PICTURE_TYPE_PNG);
//            is.close();

            //add relation from sheet to the picture data
            String rID = sheet.addRelation(null, XSSFRelation.IMAGES, workbook.getAllPictures().get(pictureIdx)).getRelationship().getId();
            //set background picture to sheet
            sheet.getCTWorksheet().addNewPicture().setId(rID);

            workbook.write(out);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
