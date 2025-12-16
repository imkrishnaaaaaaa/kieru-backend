package com.kieru.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/test")
public class TestController {

    StringRedisTemplate redisTemplate;
    public TestController(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/test-redis")
    public String testGetMapping(){
        redisTemplate.opsForValue().set("test-key", "Hello from Kieru!");
        return String.format("Redis Value: %s", redisTemplate.opsForValue().get("test-key"));
    }

}
