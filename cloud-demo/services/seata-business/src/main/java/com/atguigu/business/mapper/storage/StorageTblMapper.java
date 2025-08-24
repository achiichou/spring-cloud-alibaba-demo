package com.atguigu.business.mapper.storage;

import com.atguigu.business.bean.StorageTbl;
import org.apache.ibatis.annotations.Param;

/**
* @author lfy
* @description 針對表【storage_tbl】的資料庫操作Mapper
* @createDate 2025-01-08 18:35:07
* @Entity com.atguigu.business.bean.StorageTbl
*/
public interface StorageTblMapper {

    int deleteByPrimaryKey(Long id);

    int insert(StorageTbl record);

    int insertSelective(StorageTbl record);

    StorageTbl selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(StorageTbl record);

    int updateByPrimaryKey(StorageTbl record);

    void deduct(@Param("commodityCode") String commodityCode, @Param("count") int count);
    
    /**
     * 根據商品編碼查詢庫存
     */
    StorageTbl selectByCommodityCode(@Param("commodityCode") String commodityCode);
    
    /**
     * 增加庫存
     */
    void addStock(@Param("commodityCode") String commodityCode, @Param("count") int count);
    
    /**
     * 設置庫存
     */
    void setStock(@Param("commodityCode") String commodityCode, @Param("count") int count);
    
    /**
     * 檢查庫存是否足夠
     */
    int checkStock(@Param("commodityCode") String commodityCode, @Param("count") int count);
    
    /**
     * 根據商品編碼刪除庫存記錄（用於測試）
     */
    int deleteByCommodityCode(@Param("commodityCode") String commodityCode);
}