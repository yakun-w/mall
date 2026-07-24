package com.macro.mall.portal.demo.mapper;

import com.macro.mall.portal.demo.entity.OmsOrder;
import com.macro.mall.portal.demo.entity.OmsOrderItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    @Insert("INSERT INTO oms_order_item(order_id,order_sn,product_id,product_name,product_price,product_quantity) " +
            "VALUES(#{orderId},#{orderSn},#{productId},#{productName},#{productPrice},#{productQuantity})")
    int insert(OmsOrderItem item);

    @Select("SELECT * FROM oms_order_item WHERE order_id = #{orderId}")
    List<OmsOrderItem> listByOrderId(Long orderId);

    /**
     * 根据购物车ID列表查询商品信息
     * @param cartIds 购物车ID集合
     */
    @Select("<script>" +
            "SELECT * FROM oms_cart_item " +
            "WHERE id IN " +
            "<foreach collection='cartIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<OmsOrderItem> selectByCartIds(@Param("cartIds") List<Long> cartIds);
}
