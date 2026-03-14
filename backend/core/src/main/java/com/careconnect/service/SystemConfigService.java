package com.careconnect.service;

import com.careconnect.model.SystemConfig;
import com.careconnect.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SystemConfigService {
    private final SystemConfigRepository repo;

    public SystemConfigService(SystemConfigRepository repo) {
        this.repo = repo;
    }

    public Optional<String> getValue(String key) {
        return repo.findByConfigKey(key).map(SystemConfig::getConfigValue);
    }

    @Transactional
    public void setValue(String key, String value, Long updatedByUserId) {
        SystemConfig cfg = repo.findByConfigKey(key).orElseGet(() -> SystemConfig.builder()
                .configKey(key)
                .build());
        cfg.setConfigValue(value);
        cfg.setUpdatedBy(updatedByUserId);
        repo.save(cfg);
    }
}

