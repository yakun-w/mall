package com.macro.mall.mapper;

import com.macro.mall.model.OmsCartItem1;
import com.macro.mall.model.OmsCartItem1Example;
import java.util.List;
import org.apache.ibatis.annotations.Param;

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
}