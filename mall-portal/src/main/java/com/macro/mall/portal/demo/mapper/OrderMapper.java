package com.macro.mall.portal.demo.mapper;

import com.macro.mall.portal.demo.entity.OmsOrder;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO oms_order(order_sn,total_amount,pay_amount,status) " +
            "VALUES(#{orderSn},#{totalAmount},#{payAmount},#{status})")
    @Options(useGeneratedKeys = true,keyProperty = "id")
    int insert(OmsOrder order);

    @Select("SELECT * FROM oms_order WHERE order_sn = #{orderSn}")
    OmsOrder getBySn(String orderSn);

    @Update("UPDATE oms_order SET status = #{status} WHERE order_sn = #{orderSn}")
    int updateStatus(@Param("orderSn") String orderSn,@Param("status") int status);
}