package com.leyou.gateway.filter;

import com.leyou.common.utils.CookieUtils;
import com.leyou.common.utils.JwtUtils;
import com.leyou.gateway.config.FilterProperties;
import com.leyou.gateway.config.JwtProperties;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Component
@EnableConfigurationProperties({JwtProperties.class , FilterProperties.class})
public class LoginFilter extends ZuulFilter {

    @Autowired
    private JwtProperties prop;

    @Autowired
    private FilterProperties filterProperties;

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 10;
    }

    @Override
    public boolean shouldFilter() {
        //获取白名单
        List<String> allowPaths = this.filterProperties.getAllowPaths();
        //初始化运行上下文
        RequestContext currentContext = RequestContext.getCurrentContext();
        //获取当前请求路径
        HttpServletRequest request = currentContext.getRequest();
        String url = request.getRequestURI().toString();
        for (String allowPath : allowPaths) {
            if (StringUtils.contains(url , allowPath)){
                return false;
            }
        }
        return true;
    }

    @Override
    public Object run() throws ZuulException {

        //初始化运行上下文
        RequestContext currentContext = RequestContext.getCurrentContext();

        String token = CookieUtils.getCookieValue(currentContext.getRequest(), prop.getCookieName());
        if (StringUtils.isBlank(token)){
            //设置不转发请求
            currentContext.setSendZuulResponse(false);
            //设置返回状态码
            currentContext.setResponseStatusCode(HttpStatus.UNAUTHORIZED.value());
        }
        try {
            JwtUtils.getInfoFromToken(token , prop.getPublicKey());
        } catch (Exception e) {
            currentContext.setSendZuulResponse(false);
            e.printStackTrace();
        }
        return null;
    }
}
