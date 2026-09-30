package com.foodtrace.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置
 *
 * <p>注册分页内拦截器，使 selectPage 生效（缺失时查询不追加 LIMIT）。
 *
 * @author Microft0629
 * @since 2026-09-30
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器
     *
     * @return 含 MySQL 方言分页的拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
