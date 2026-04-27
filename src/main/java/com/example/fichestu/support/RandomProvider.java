package com.example.fichestu.support;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class RandomProvider {

    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public int nextInt(int boundExclusive) {
        return ThreadLocalRandom.current().nextInt(boundExclusive);
    }
}
