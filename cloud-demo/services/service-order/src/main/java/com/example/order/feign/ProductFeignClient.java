package com.example.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.order.feign.fallback.ProductFeignClientFallback;
import com.example.product.Product;

/**
 * order 服務啟動 -> 從 Nacos 獲取所有已註冊的服務列表，緩存在本地
 * 調用：Feign 從本地緩存中查找 service-product 的服務實例
 */
@FeignClient(name = "service-product", fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {

    @GetMapping("/product/{id}")
    Product getProductById(@PathVariable Long id);
}

/**
 * 實際的調用流程：
 * 啟動時：從 Nacos 獲取所有服務實例信息並緩存
 * 調用時：從本地緩存選擇實例，如果緩存過期則重新從 Nacos 獲取
 * 負載均衡：在可用的實例中選擇一個進行調用
 * 
 * 緩存更新機制：
 * Spring Cloud 會定期從 Nacos 獲取最新的服務實例信息
 * 當服務實例上下線時，會自動更新本地緩存
 * 確保調用的服務實例是最新的
 */