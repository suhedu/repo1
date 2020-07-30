package com.leyou.item.api;

import com.leyou.common.pojo.PageResult;
import com.leyou.item.bo.SpuBo;
import com.leyou.item.pojo.Sku;
import com.leyou.item.pojo.Spu;
import com.leyou.item.pojo.SpuDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface GoodsApi {

    /**
     * 根据spuid查询SpuDetail
     * @param spuid
     * @return
     */
    @GetMapping("spu/detail/{spuid}")
    public SpuDetail querySpuDetailBySpuId(@PathVariable("spuid")Long spuid);


    /**
     * 根据条件分页查询Spu
     * @param key
     * @param saleable
     * @param page
     * @param rows
     * @return
     */
    @GetMapping("spu/page")
    public PageResult<SpuBo> querySpuByPage(
            @RequestParam(value = "key" , required = false) String key,
            @RequestParam(value = "saleable" , required = false) Boolean saleable,
            @RequestParam(value = "page" , defaultValue = "1") Integer page,
            @RequestParam(value = "rows" , defaultValue = "5") Integer rows
    );

    /**
     * 根据spuid查询Sku集合
     * @param spuid
     * @return
     */
    @GetMapping("sku/list")
    public List<Sku> querySkusBySpuId(@RequestParam("id")Long spuid);

    /**
     * 根据id查询spu
     * @param id
     * @return
     */
    @GetMapping("{id}")
    public Spu querySpuById(@PathVariable("id")Long id);

    /**
     * 根据skuID查询Sku
     * @return
     */
    @GetMapping("sku/{skuId}")
    public Sku querySkuById(@PathVariable("skuId")Long skuId);
}
