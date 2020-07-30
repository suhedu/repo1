package com.leyou.goods.service;

import com.leyou.goods.client.BrandClient;
import com.leyou.goods.client.CategoryClient;
import com.leyou.goods.client.GoodsClient;
import com.leyou.goods.client.SpecificationClient;
import com.leyou.item.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GoodsService {

    @Autowired
    private BrandClient brandClient;
    @Autowired
    private CategoryClient categoryClient;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private SpecificationClient specificationClient;

    /**
     * 根据spuId查询所需要的所有数据，封装为Map，key为数据的名称，value为数据的值
     * @param spuId
     * @return
     */
    public Map<String , Object> loadData(Long spuId){

        //初始化Map结果集
        Map<String , Object> model = new HashMap<>();

        //查询spu
        Spu spu = this.goodsClient.querySpuById(spuId);

        //查询spuDetail
        SpuDetail spuDetail = this.goodsClient.querySpuDetailBySpuId(spuId);

        //查询Brand
        Brand brand = this.brandClient.queryBrandById(spu.getBrandId());

        //查询categories：Map<String , Object>
        List<Long> cids = Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3());
        List<String> names = this.categoryClient.queryNamesByIds(cids);
        //初始换Map
        List<Map<String , Object>> categories = new ArrayList<>();
        for (int i = 0; i < cids.size(); i++) {
            Map<String , Object> map = new HashMap<>();
            map.put("id" , cids.get(i));
            map.put("name" , names.get(i));
            categories.add(map);
        }

        //查询skus
        List<Sku> skus = this.goodsClient.querySkusBySpuId(spuId);

        //查询groups
        List<SpecGroup> specGroups = this.specificationClient.queryGroupsWithParam(spu.getCid3());

        //查询paramMap
        List<SpecParam> params = this.specificationClient.queryParams(null, spu.getCid3(), false, null);
        Map<Long , String> paramMap = new HashMap<>();
        params.forEach(param -> {
            paramMap.put(param.getId() , param.getName());
        });

        //将数据传入model
        model.put("spu" , spu);
        model.put("categories" , categories);
        model.put("brand" , brand);
        model.put("skus" , skus);
        model.put("spuDetail" , spuDetail);
        model.put("groups" , specGroups);
        model.put("paramMap" , paramMap);

        return model;
    }
}
