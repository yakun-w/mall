package com.macro.mall.portal.demo.mapper;

import com.macro.mall.portal.demo.entity.OmsOrder;
import com.macro.mall.portal.demo.entity.OmsOrderItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    @Insert("INSERT INTO oms_order_item(order_id,product_name,product_price,product_quantity) " +
            "VALUES(#{orderId},#{productName},#{productPrice},#{productQuantity})")
    int insert(OmsOrderItem item);

    @Select("SELECT * FROM oms_order_item WHERE order_id = #{orderId}")
    List<OmsOrderItem> listByOrderId(Long orderId);
}
