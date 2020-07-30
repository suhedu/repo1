package com.leyou.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leyou.item.pojo.*;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.client.SpecificationClient;
import com.leyou.search.pojo.Goods;
import com.leyou.search.pojo.SearchRequest;
import com.leyou.search.pojo.SearchResult;
import com.leyou.search.repository.GoodsRepository;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private CategoryClient categoryClient;

    @Autowired
    private BrandClient brandClient;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    SpecificationClient specificationClient;

    @Autowired
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private GoodsRepository goodsRepository;

    public Goods buildGoods(Spu spu) throws IOException {

        Goods goods = new Goods();

        //根据分类的id查询分类的名称
        List<String> names = this.categoryClient.queryNamesByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));

        //根据品牌id查询品牌
        Brand brand = this.brandClient.queryBrandById(spu.getBrandId());


        //根据spuid查询所有的sku
        List<Sku> skus = this.goodsClient.querySkusBySpuId(spu.getId());
        //List<Long> prices = skus.stream().map(sku -> sku.getPrice()).collect(Collectors.toList());
        List<Long> prices = new ArrayList<>();
        //收集sku的必要信息,实际开发中，sku的信息很多
        List<Map<String , Object>> skuMapList = new ArrayList<>();
        skus.forEach(sku -> {
            prices.add(sku.getPrice());

            Map<String , Object> map = new HashMap<>();
            map.put("id" , sku.getId());
            map.put("title" , sku.getTitle());
            map.put("price" , sku.getPrice());
            //数据库的图片可能没有，也可能有多张，并以“，”分割
            map.put("image" , StringUtils.isBlank(sku.getImages()) ? " " : StringUtils.split(sku.getImages() , ",")[0]);
            skuMapList.add(map);
        });

        //根据cid3查询对应的搜索规格参数
        List<SpecParam> specParams = this.specificationClient.queryParams(null, spu.getCid3(), null, true);
        //根据spuid查询spuDetail
        SpuDetail spuDetail = this.goodsClient.querySpuDetailBySpuId(spu.getId());
        //将字符串反序列化Map(通用的规格参数)
        Map<String , Object>  genericSpecMap = MAPPER.readValue(spuDetail.getGenericSpec(), new TypeReference<Map<String, Object>>(){});
        //将字符串反序列化Map(特殊的规格参数)
        Map<String , List<Object>>  specialSpecSpecMap = MAPPER.readValue(spuDetail.getSpecialSpec(), new TypeReference<Map<String, List<Object>>>(){});
        Map<String ,Object> specs = new HashMap<>();
        specParams.forEach(param -> {
            //判断规格参数是否是通用的
            if (param.getGeneric()){
                //是通用类型
                String value = genericSpecMap.get(param.getId().toString()).toString();
                //判断是否是数值类型参数，返回区间
                if (param.getNumeric()){
                value = chooseSegment(value, param);
                }
                specs.put(param.getName() , value);
                //特殊的规格参数
            }else{
                List<Object> value = specialSpecSpecMap.get(param.getId().toString());
                specs.put(param.getName() , value);
            }
        });

        goods.setId(spu.getId());
        goods.setCid1(spu.getCid1());
        goods.setCid2(spu.getCid2());
        goods.setCid3(spu.getCid3());
        goods.setBrandId(spu.getBrandId());
        goods.setCreateTime(spu.getCreateTime());
        goods.setSubTitle(spu.getSubTitle());
        //拼接all字段
        goods.setAll(spu.getTitle() + "  " + StringUtils.join(names , " ") + " " + brand.getName());
        //获取spu下所有sku的价格
        goods.setPrice(prices);
        //获取spu下的所有sku，并转化为json字符串
        goods.setSkus(MAPPER.writeValueAsString(skuMapList));
        //获取查询的所有规格参数
        goods.setSpecs(specs);

        return goods;
    }


    /**
     *参数值为区间时用于判断的方法
     * @param value
     * @param p
     * @return
     */
    private String chooseSegment(String value, SpecParam p) {
        double val = NumberUtils.toDouble(value);
        String result = "其它";
        // 保存数值段
        for (String segment : p.getSegments().split(",")) {
            String[] segs = segment.split("-");
            // 获取数值范围
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if(segs.length == 2){
                end = NumberUtils.toDouble(segs[1]);
            }
            // 判断是否在范围内
            if(val >= begin && val < end){
                if(segs.length == 1){
                    result = segs[0] + p.getUnit() + "以上";
                }else if(begin == 0){
                    result = segs[1] + p.getUnit() + "以下";
                }else{
                    result = segment + p.getUnit();
                }
                break;
            }
        }
        return result;
    }

    public SearchResult search(SearchRequest request) {
        if (StringUtils.isBlank(request.getKey())) {
            return null;
        }
        // 构建查询条件
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

        //查询条件
        //QueryBuilder basicQuery = QueryBuilders.matchQuery("all", request.getKey()).operator(Operator.AND);
        BoolQueryBuilder basicQuery = buildBoolQueryBuilder(request);//实现选中例如（品牌，内存大小）后，可搜索参数的改变
        //对key进行全文检索查询
        queryBuilder.withQuery(basicQuery);

        //通过sourceFilter设置返回的结果字段,我们只需要id(点击跳转到详情页)、skus、subTitle
        queryBuilder.withSourceFilter(new FetchSourceFilter(
                new String[]{"id","skus","subTitle"}, null));


        //准备分页参数,注意，页码从0开始;
        queryBuilder.withPageable(PageRequest.of(request.getPage()- 1, request.getSize()));

        //添加分类和品牌的聚合
        String categorieAggName = "categories";//抽取聚合名称，方便之后使用
        String brandAggName = "brands";
        //添加聚合
        queryBuilder.addAggregation(AggregationBuilders.terms(categorieAggName).field("cid3"));
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));

        //查询，获取结果
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>)this.goodsRepository.search(queryBuilder.build());

        //获取聚合结果集，并解析
        List<Map<String, Object>> categories = getCategoryAggResult(goodsPage.getAggregation(categorieAggName));//getCategoryAggResult里面的参数是StringTerms
        List<Brand> brands = getBrandAggResult(goodsPage.getAggregation(brandAggName));

        //判断是否是一个分类，只有一个分类时，才做规格参数的聚合
        List<Map<String , Object>> specs = new ArrayList<>();
        if (!CollectionUtils.isEmpty(categories) && categories.size() == 1){
            //对规格参数进行聚合
            specs = getParamAggResult((Long)categories.get(0).get("id") , basicQuery);
        }
        // 封装结果并返回
        return new SearchResult(goodsPage.getTotalElements(), goodsPage.getTotalPages(), goodsPage.getContent() , categories , brands , specs);
    }


    /**
     *构建布尔查询
     * @param request
     * @return
     */
    private BoolQueryBuilder buildBoolQueryBuilder(SearchRequest request) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //给布尔查询添加基本的查询字段
        boolQueryBuilder.must(QueryBuilders.matchQuery("all" , request.getKey()).operator(Operator.AND));
        //添加过滤条件
        //获取用户选择的过滤信息
        Map<String, Object> filter = request.getFilter();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            //获取Key值用于判断是否为品牌或分类
            String key = entry.getKey();
            if (StringUtils.equals("品牌" , key)){
                key = "brandId";
            } else if (StringUtils.equals("分类" , key)){
                key = "cid3";
            } else{
                key = "specs." + key + ".keyword";
            }
            boolQueryBuilder.filter(QueryBuilders.termQuery(key , entry.getValue()));
        }
        return boolQueryBuilder;
    }


    /**
     * 根据查询条件聚合规格参数
     * @param cid
     * @param basicQuery
     * @return
     */
    private List<Map<String, Object>>  getParamAggResult(Long cid, QueryBuilder basicQuery) {
        //自定义查询条件构建
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        //添加基本的查询条件
        queryBuilder.withQuery(basicQuery);
        //查询要聚合的规格参数
        List<SpecParam> params = this.specificationClient.queryParams(null, cid, null, true);
        //添加规格参数的聚合,注意字符串拼接时不要忘记","。
        params.forEach(param -> {
            queryBuilder.addAggregation(AggregationBuilders.terms(param.getName()).field("specs." + param.getName() + ".keyword"));
        });
        //添加结果集过滤
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{} , null));
        //执行聚合查询
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>)this.goodsRepository.search(queryBuilder.build());
        //解析聚合结果集,key聚合名称，value聚合对象
        Map<String, Aggregation> aggregationMap = goodsPage.getAggregations().asMap();
        //初始化返回集合
        List<Map<String, Object>> specs = new ArrayList<>();
        //遍历Map
        for (Map.Entry<String, Aggregation> entry : aggregationMap.entrySet()) {
            //初始化一个map，key规格参数名，options聚合的规格参数值
            Map<String, Object> map = new HashMap<>();
            map.put("k" , entry.getKey());
            //将Aggregation强转，获取桶（bucket）集合
            List<StringTerms.Bucket> buckets = ((StringTerms)entry.getValue()).getBuckets();
            //将buckets用stream转化为所需要的集合
            List<Object> options = buckets.stream().map(bucket -> bucket.getKeyAsString()).collect(Collectors.toList());
            map.put("options" , options);//此map中，key为聚合的名称，如“内存”，value是List：4G,8G...
            specs.add(map);  //ThymeleafProperties
        }
        return specs;
    }

    /**
     * 解析分类的聚合结果集
     * @param aggregation
     * @return
     */
    private List<Map<String, Object>> getCategoryAggResult(Aggregation aggregation) {
        LongTerms longTerms = (LongTerms)aggregation;
        //获取桶的集合，然后用stream转化为需要的返回集合
        return longTerms.getBuckets().stream().map(bucket -> {
            //初始化Map
            Map<String , Object> map = new HashMap<>();
            //获取桶中的分类id
            Long id = bucket.getKeyAsNumber().longValue();
            //根据分类id查询分类名称
            List<String> names = this.categoryClient.queryNamesByIds(Arrays.asList(id));
            map.put("id" , id);
            map.put("name" , names.get(0));
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 解析品牌的聚合结果集
     * @param aggregation
     * @return
     */
    private List<Brand> getBrandAggResult(Aggregation aggregation){
        LongTerms longTerms = (LongTerms)aggregation;

        List<Brand> brands = new ArrayList<>();
        //获取桶
        longTerms.getBuckets().forEach(bucket -> {
            Brand brand = brandClient.queryBrandById(bucket.getKeyAsNumber().longValue());
            brands.add(brand);
        });
        return brands;
    }

    /**
     * 新增商品到索引库(rabbitMQ)
     * @param id
     */
    public void save(Long id) throws IOException {
        Spu spu = this.goodsClient.querySpuById(id);

        //根据spu构建goods
        Goods goods = this.buildGoods(spu);

        //使用goodsRepository的save保存goods至索引库
        this.goodsRepository.save(goods);
    }
}

