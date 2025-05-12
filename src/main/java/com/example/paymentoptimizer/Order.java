package com.example.paymentoptimizer;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Order(
        String id,
        BigDecimal value,
        List<String> promotions) {

    @JsonCreator
    public Order(
            @JsonProperty("id") String id,
            @JsonProperty("value") String value,
            @JsonProperty("promotions") List<String> promotions) {
        this(id, new BigDecimal(value), promotions == null ? List.of() : promotions);
    }
}
