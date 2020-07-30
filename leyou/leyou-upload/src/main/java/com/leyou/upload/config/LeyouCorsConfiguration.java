package com.leyou.upload.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class LeyouCorsConfiguration {

    @Bean
    public CorsFilter corsFilter(){
        //初始化cors配置对象
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.addAllowedOrigin("http://manage.leyou.com");//允许跨域的域名
        corsConfiguration.setAllowCredentials(true);//是否允许携带cookie，如果允许，则上面不能为*
        corsConfiguration.addAllowedMethod("*");//*代表所有的请求方法
        corsConfiguration.addAllowedHeader("*");//允许携带任何的头信息
        //初始换cors配置源对象
        UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();
        configurationSource.registerCorsConfiguration("/**" , corsConfiguration);
        //参数：cors配置源对象
        return new CorsFilter(configurationSource);
    }
}
