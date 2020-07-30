package com.leyou.item.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.leyou.common.pojo.PageResult;
import com.leyou.item.bo.SpuBo;
import com.leyou.item.mapper.*;
import com.leyou.item.pojo.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoodsService {

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private SpuDetailMapper spuDetailMapper;

    @Autowired
    private BrandMapper brandMapper;

    @ Autowired
    private CategoryService categoryService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;


    /**
     * 根据条件分页查询Spu
     * @param key
     * @param saleable
     * @param page
     * @param rows
     * @return
     */
    public PageResult<SpuBo> querySpuByPage(String key, Boolean saleable, Integer page, Integer rows) {

        Example example = new Example(Spu.class);
        Example.Criteria criteria = example.createCriteria();

        //添加查询条件
        if (StringUtils.isNotBlank(key)){
            criteria.andLike("title" , "%" + key + "%");
        }
        //添加上下架的过滤条件
        if (saleable != null){
            criteria.andEqualTo("saleable" , saleable);
        }
        //添加分页
        PageHelper.startPage(page , rows);
        //执行查询，获取Spu集合
        List<Spu> spus = this.spuMapper.selectByExample(example);
        //获取总条数
        PageInfo<Spu> pageInfo = new PageInfo<>(spus);
        //Spu集合转化为SpuBo集合
        List<SpuBo> spuBos = spus.stream().map(spu -> {
            SpuBo spuBo = new SpuBo();
            BeanUtils.copyProperties(spu , spuBo);
            //查询品牌名称
            Brand brand = this.brandMapper.selectByPrimaryKey(spuBo.getBrandId());
            spuBo.setBname(brand.getName());
            //查询分类名称
            List<String> cnames = this.categoryService.queryNamesByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));
            spuBo.setCname(StringUtils.join(cnames , "-"));
            return  spuBo;
        }).collect(Collectors.toList());
        //返回结果集
        return new PageResult<>(pageInfo.getTotal() , spuBos);
    }


    @Transactional
    public void saveGoods(SpuBo spuBo) {
        //新增spu
        spuBo.setId(null);//为了防止恶意注入
        spuBo.setSaleable(true);
        spuBo.setValid(true);
        spuBo.setCreateTime(new Date());
        spuBo.setLastUpdateTime(spuBo.getCreateTime());
        this.spuMapper.insertSelective(spuBo);
        //新增spuDetail
        SpuDetail spuDetail = spuBo.getSpuDetail();
        spuDetail.setSpuId(spuBo.getId());
        this.spuDetailMapper.insertSelective(spuDetail);
        //新增sku和stock
        saveSkuAndStock(spuBo);

        sendMsg("insert" , spuBo.getId());
    }


    /**
     * 根据spuid查询SpuDetail
     * @param spuid
     * @return
     */
    public SpuDetail querySpuDetailBySpuId(Long spuid) {
        return this.spuDetailMapper.selectByPrimaryKey(spuid);
    }


    /**
     * 根据spuid查询Sku集合
     * @param spuid
     * @return
     */
    public List<Sku> querySkusBySpuId(Long spuid) {
        Sku record = new Sku();
        record.setSpuId(spuid);
        List<Sku> skus = this.skuMapper.select(record);
        skus.forEach(sku -> {
            sku.setStock(this.stockMapper.selectByPrimaryKey(sku.getId()).getStock());
        });
        return skus;
    }

    @Transactional
    public void updateGoods(SpuBo spuBo) {
        // 查询以前sku
        Sku record = new Sku();
        record.setSpuId(spuBo.getId());
        List<Sku> skus = this.skuMapper.select(record);
        skus.forEach(sku -> {
            //删除库存
            stockMapper.deleteByPrimaryKey(sku.getId());
            // 删除以前的sku
            skuMapper.delete(record);

        });
        // 新增sku和库存
       this.saveSkuAndStock(spuBo);
        // 更新spu
        spuBo.setCreateTime(null);
        spuBo.setLastUpdateTime(new Date());
        spuBo.setValid(null);
        spuBo.setSaleable(null);
        this.spuMapper.updateByPrimaryKeySelective(spuBo);
        // 更新spuDtail
        this.spuDetailMapper.updateByPrimaryKeySelective(spuBo.getSpuDetail());

        this.sendMsg("update" , spuBo.getId());
    }

    /**
     * 新增sku和stock
     * @param spuBo
     */
    private void saveSkuAndStock(SpuBo spuBo) {
        spuBo.getSkus().forEach(sku -> {
            //新增sku
            sku.setId(null);
            sku.setSpuId(spuBo.getId());
            sku.setCreateTime(new Date());
            sku.setLastUpdateTime(sku.getCreateTime());
            this.skuMapper.insertSelective(sku);
            //新增stock
            Stock stock = new Stock();
            stock.setSkuId(sku.getId());
            stock.setStock(sku.getStock());
            this.stockMapper.insertSelective(stock);
        });
    }

    /**
     * 根据id查询spu
     * @param id
     * @return
     */
    public Spu querySpuById(Long id) {
        return this.spuMapper.selectByPrimaryKey(id);
    }

    /**
     * 发送消息到队列
     * @param type
     * @param id
     */
    private void sendMsg(String type , Long id) {
        try {
            this.amqpTemplate.convertAndSend("item." + type , id);
        } catch (AmqpException e) {
            e.printStackTrace();
        }
    }

    public Sku querySkuById(Long skuId) {
        return  this.skuMapper.selectByPrimaryKey(skuId);
    }
}
