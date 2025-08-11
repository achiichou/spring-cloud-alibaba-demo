package com.example.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.order.feign.WeatherFeignClient;

/**
 * 第三方API接口範例，使用FeignClient來調用
 */
@SpringBootTest
public class WeatherFeignTest {

    @Autowired
    private WeatherFeignClient weatherFeignClient;

    @Test
    void testWeatherFeign() {
        String auth = "";
        String token = "";
        String weather = weatherFeignClient.getWeatherByCity(auth, token, "Taipei");
        System.out.println(weather);
    }
}
