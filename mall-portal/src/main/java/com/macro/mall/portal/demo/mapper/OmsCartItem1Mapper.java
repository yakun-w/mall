package com.macro.mall.portal.demo.mapper;

import com.macro.mall.model.OmsCartItem1;
import com.macro.mall.model.OmsCartItem1Example;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OmsCartItem1Mapper {
    long countByExample(OmsCartItem1Example example);

    int deleteByExample(OmsCartItem1Example example);

    int deleteByPrimaryKey(Long id);

    int insert(OmsCartItem1 row);

    int insertSelective(OmsCartItem1 row);

    List<OmsCartItem1> selectByExample(OmsCartItem1Example example);

    OmsCartItem1 selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") OmsCartItem1 row, @Param("example") OmsCartItem1Example example);

    int updateByExample(@Param("row") OmsCartItem1 row, @Param("example") OmsCartItem1Example example);

    int updateByPrimaryKeySelective(OmsCartItem1 row);

    int updateByPrimaryKey(OmsCartItem1 row);

    @Select("<script>" +
            "SELECT id, product_id, member_id, quantity, price, product_name FROM oms_cart_item1 " +
            "WHERE id IN " +
            "<foreach collection='cartIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<OmsCartItem1> selectByCartIds(@Param("cartIds") List<Long> cartIds);
}