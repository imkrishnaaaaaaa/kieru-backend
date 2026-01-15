package com.kieru.backend.controller;


import com.kieru.backend.dto.AssetsDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/assets")
public class AssetsController {

    @GetMapping("/bgImg")
    public ResponseEntity<AssetsDTO> getBgImg() {
        AssetsDTO dto = AssetsDTO.builder().result("https://hd.unsplash.com/photo-1416339306562-f3d12fefd36f").build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(dto);
    }
}