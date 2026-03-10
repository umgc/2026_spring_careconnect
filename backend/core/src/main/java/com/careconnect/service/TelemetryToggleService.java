package com.careconnect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TelemetryToggleService {
    private final AtomicBoolean enabled;

    public TelemetryToggleService(@Value("${telemetry.enabled:true}") boolean defaultEnabled) {
        this.enabled = new AtomicBoolean(defaultEnabled);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public boolean setEnabled(boolean value) {
        enabled.set(value);
        return enabled.get();
    }
}