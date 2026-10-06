package com.cabinet.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 支持 Hibernate6：序列化 JPA 实体时强制加载懒关联
 * （已配合 spring.open-in-view=true），避免关联对象被输出为 null，
 * 同时避免 ByteBuddyInterceptor 序列化异常。
 */
@Configuration
public class JacksonHibernateConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        module.enable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        return module;
    }
}
