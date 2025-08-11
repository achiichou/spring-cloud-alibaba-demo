package com.example.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * 第三方API接口範例，使用FeignClient來調用
 */
@FeignClient(value = "weather-client", url = "http://getweather")
public interface WeatherFeignClient {

    @GetMapping("/weather/{city}")
    String getWeatherByCity(@RequestHeader("Authorization") String auth, @RequestParam("token") String token, @PathVariable String city);
}
