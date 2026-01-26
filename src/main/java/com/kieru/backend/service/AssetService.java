package com.kieru.backend.service;

import java.util.Map;

public interface AssetService {
    Map<String, String> getSubscriptionNames();
    Map<String, Integer> getCharLimits();
    Map<String, Long> getFileSizeLimits();
}