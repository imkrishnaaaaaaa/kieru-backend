package com.kieru.backend.service.impl;

import com.kieru.backend.service.AssetService;
import com.kieru.backend.util.KieruUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetServiceImpl implements AssetService {

    private final KieruUtil kieruUtil;

    @Override
    public Map<String, String> getSubscriptionNames() {
        return Arrays.stream(KieruUtil.SubscriptionPlan.values())
                .collect(Collectors.toMap(Enum::name, KieruUtil.SubscriptionPlan::getName));
    }

    @Override
    public Map<String, Integer> getCharLimits() {
        return Arrays.stream(KieruUtil.SubscriptionPlan.values())
                .collect(Collectors.toMap(
                        KieruUtil.SubscriptionPlan::getName,
                        kieruUtil::getUserCharLimit
                ));
    }

    @Override
    public Map<String, Long> getFileSizeLimits() {
        return Arrays.stream(KieruUtil.SubscriptionPlan.values())
                .collect(Collectors.toMap(
                        KieruUtil.SubscriptionPlan::getName,
                        plan -> (long) kieruUtil.getUserFileSizeLimit(plan)
                ));
    }

    @Override
    public Map<String, Integer> getDailySecretLimits() {
        return Arrays.stream(KieruUtil.SubscriptionPlan.values())
                .collect(Collectors.toMap(
                        KieruUtil.SubscriptionPlan::getName,
                        kieruUtil::getUserDailyCreateLimit
                ));
    }
}