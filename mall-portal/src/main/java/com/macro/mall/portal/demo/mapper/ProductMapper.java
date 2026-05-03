package com.macro.mall.portal.demo.mapper;

import com.macro.mall.portal.demo.entity.OmsOrder;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProductMapper {

    @Update("UPDATE pms_product SET stock = stock - #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity}")
    int reduceStock(@Param("productId") Long productId,
                    @Param("quantity") int quantity);

    @Update("UPDATE pms_product SET stock = stock + #{quantity} " +
            "WHERE id = #{productId}")
    int addStock(@Param("productId") Long productId,
                 @Param("quantity") int quantity);
}