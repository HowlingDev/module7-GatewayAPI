package com.example.config;

import org.springframework.stereotype.Component;

@Component
public class CircuitBreaker {

    private boolean isOpen = false;

    public boolean isServiceAvailable() {
        return !isOpen;
    }

    public void openCircuit() {
        isOpen = true;
    }

    public void closeCircuit() {
        isOpen = false;
    }
}
