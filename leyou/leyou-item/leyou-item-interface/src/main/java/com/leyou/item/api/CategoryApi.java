package com.leyou.item.api;


import com.leyou.item.pojo.Category;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequestMapping("category")
public interface CategoryApi {


    /**
     * 根据父节点的Id查询子节点
     * @param pid
     * @return
     */
    @GetMapping("list")
    public List<Category> queryCategoriesByPid(@RequestParam(value = "pid" , defaultValue = "0") Long pid);

    @GetMapping("names")
    public List<String> queryNamesByIds(@RequestParam("ids")List<Long> ids);
}
