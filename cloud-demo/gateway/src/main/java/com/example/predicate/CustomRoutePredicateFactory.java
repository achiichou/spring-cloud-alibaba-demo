package com.example.predicate;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import jakarta.validation.constraints.NotEmpty;

/**
 * 自訂斷言
 * 使用此斷言，在application中用Custom(CustomRoutePredicateFactory去除RoutePredicateFactory)
 * ex:Path斷言使用：PathRoutePredicateFactory
 */
@Component
public class CustomRoutePredicateFactory extends AbstractRoutePredicateFactory<CustomRoutePredicateFactory.Config> {

    public CustomRoutePredicateFactory() {
        super(Config.class);
    }

    /**
     * 簡短寫法
     */
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("param", "value");
    }    

    /**
     * 自訂參數的規則
     * 規則的值寫在application中
     */
    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return new Predicate<ServerWebExchange>() {

            @Override
            public boolean test(ServerWebExchange exchange) {
                ServerHttpRequest request = exchange.getRequest();
                String paramValue = request.getQueryParams().getFirst(config.getParam());
                return paramValue != null && paramValue.equals(config.getValue());
            }

        };
    }

    /**
     * 自訂斷言的設定
     * 可以自己設定Config內要有那些參數
     */
    @Validated
    public static class Config {

        @NotEmpty
        private String param;
        
        @NotEmpty
        private String value;

        public String getParam() {
            return param;
        }

        public String getValue() {
            return value;
        }

        public void setParam(String param) {
            this.param = param;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
