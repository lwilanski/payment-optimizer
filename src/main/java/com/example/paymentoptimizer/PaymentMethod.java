package com.example.paymentoptimizer;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PaymentMethod {
    public final String id;
    public final int discountPercent;
    public final BigDecimal initialLimit;
    private BigDecimal remaining;

    @JsonCreator
    public PaymentMethod(
            @JsonProperty("id") String id,
            @JsonProperty("discount") String discount,
            @JsonProperty("limit") String limit) {
        this.id = id;
        this.discountPercent = Integer.parseInt(discount);
        this.initialLimit = new BigDecimal(limit);
        this.remaining = initialLimit;
    }

    public BigDecimal getRemaining() {
        return remaining;
    }

    public boolean hasCapacity(BigDecimal amount) {
        return remaining.compareTo(amount) >= 0;
    }

    public void spend(BigDecimal amount) {
        if (!hasCapacity(amount)) {
            throw new IllegalArgumentException("Limit exceeded for " + id);
        }
        remaining = remaining.subtract(amount);
    }
}
