package com.kieru.backend.service.impl;

import com.kieru.backend.service.AssetService;
import com.kieru.backend.util.KieruUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final KieruUtil kieruUtil;

    @Override
    public Map<String, String> getSubscriptionNames() {
        // Converts Enum: ANONYMOUS -> "anonymous"
        return Arrays.stream(KieruUtil.SubscriptionPlan.values())
                .collect(Collectors.toMap(Enum::name, KieruUtil.SubscriptionPlan::getName));
    }

    @Override
    public Map<String, Integer> getCharLimits() {
        // Dynamically fetch limits from KieruUtil (which reads from Config/Properties)
        return Arrays.stream(KieruUtil.SubscriptionPlan.values())
                .collect(Collectors.toMap(
                        KieruUtil.SubscriptionPlan::getName,
                        kieruUtil::getUserCharLimit
                ));
    }

    @Override
    public Map<String, Long> getFileSizeLimits() {
        // Dynamically fetch limits from KieruUtil
        // Note: KieruUtil returns int, casting to Long for the interface contract
        return Arrays.stream(KieruUtil.SubscriptionPlan.values())
                .collect(Collectors.toMap(
                        KieruUtil.SubscriptionPlan::getName,
                        plan -> (long) kieruUtil.getUserFileSizeLimit(plan)
                ));
    }
}