package com.hmdp.config;

import com.hmdp.Interceptor.LoginInterceptor;
import com.hmdp.Interceptor.TokenReflashInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    //1.引入拦截器
    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private TokenReflashInterceptor tokenReflashInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //2.添加拦截器
        registry.addInterceptor(tokenReflashInterceptor)
                .addPathPatterns("/**");

        registry.addInterceptor(loginInterceptor)
                //3.添加拦截路径
                .addPathPatterns("/**")
                //4.添加排除路径
                .excludePathPatterns(
                        "/user/login", "/user/code",
                        "/blog/hot",
                        "/shop/**", "shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                );
    }
}
