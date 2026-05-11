package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final RefreshTokenInterceptor refreshTokenInterceptor;

    @Autowired
    public MvcConfig(LoginInterceptor loginInterceptor,RefreshTokenInterceptor refreshTokenInterceptor){
        this.loginInterceptor = loginInterceptor;
        this.refreshTokenInterceptor = refreshTokenInterceptor;
    }


    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        //注册登陆刷新拦截器
        registry.addInterceptor(refreshTokenInterceptor)
                        .addPathPatterns("/**");

        //注册登陆拦截器
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                "/shop/**",
                "/shop-type/**",
                "/voucher/**",
                "upload/**",
                "/blog/hot",
                "/user/code",
                "/user/login"
        );
    }
}
