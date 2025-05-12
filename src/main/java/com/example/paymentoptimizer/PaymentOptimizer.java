package com.example.paymentoptimizer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class PaymentOptimizer {

    private static final BigDecimal TEN = BigDecimal.valueOf(10);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java -jar app.jar <orders.json> <paymentmethods.json>");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Order> orders = mapper.readValue(new File(args[0]), new TypeReference<>() {});
        List<PaymentMethod> methodsList = mapper.readValue(new File(args[1]), new TypeReference<>() {});

        Map<String, PaymentMethod> methods = new HashMap<>();
        for (PaymentMethod pm : methodsList) {
            methods.put(pm.id, pm);
        }

        PaymentMethod points = methods.get("PUNKTY");
        if (points == null) {
            System.err.println("Missing PUNKTY method");
            System.exit(1);
        }

        // Sort orders descending by value to handle big orders first
        orders.sort(Comparator.comparing(Order::value).reversed());

        Map<String, BigDecimal> spent = new HashMap<>();

        for (Order order : orders) {
            BigDecimal value = order.value();

            Plan bestPlan = chooseBestPlan(order, value, points, methods);
            applyPlan(bestPlan, spent);
        }

        // Print result
        spent.forEach((id, amount) ->
                System.out.printf(Locale.US, "%s %.2f%n", id, amount.setScale(2, RoundingMode.HALF_UP)));
    }

    private static Plan chooseBestPlan(Order order,
                                       BigDecimal value,
                                       PaymentMethod points,
                                       Map<String, PaymentMethod> methods) {

        Plan best = null;

        // Points only
        if (points.hasCapacity(value)) {
            BigDecimal discount = value.multiply(BigDecimal.valueOf(points.discountPercent)).divide(HUNDRED);
            best = new Plan("POINTS_ONLY", discount, Map.of(points, value));
        }

        // Card promo
        PaymentMethod bestCard = null;
        int bestCardPercent = 0;
        for (String promo : order.promotions()) {
            PaymentMethod pm = methods.get(promo);
            if (pm != null && !"PUNKTY".equals(pm.id) && pm.discountPercent > bestCardPercent) {
                bestCard = pm;
                bestCardPercent = pm.discountPercent;
            }
        }
        if (bestCard != null && bestCard.hasCapacity(value)) {
            BigDecimal discount = value.multiply(BigDecimal.valueOf(bestCard.discountPercent)).divide(HUNDRED);
            Plan plan = new Plan("CARD_PROMO", discount, Map.of(bestCard, value));
            if (isBetter(plan, best)) best = plan;
        }

        // Split
        BigDecimal minPoints = value.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.CEILING);
        if (points.hasCapacity(minPoints)) {
            PaymentMethod cardForSplit = findCardWithCapacity(methods.values(), value.subtract(minPoints));
            if (cardForSplit != null) {
                BigDecimal discount = value.multiply(BigDecimal.valueOf(0.1)); // 10%
                Map<PaymentMethod, BigDecimal> map = new LinkedHashMap<>();
                map.put(points, minPoints);
                map.put(cardForSplit, value.subtract(minPoints));
                Plan plan = new Plan("SPLIT", discount, map);
                if (isBetter(plan, best)) best = plan;
            }
        }

        // Card without promo
        PaymentMethod fallbackCard = findCardWithCapacity(methods.values(), value);
        if (fallbackCard != null) {
            Plan plan = new Plan("CARD_NO_PROMO", BigDecimal.ZERO, Map.of(fallbackCard, value));
            if (best == null) best = plan;
        }

        if (best == null) {
            throw new IllegalStateException("Unable to pay for order " + order.id());
        }
        return best;
    }

    private static boolean isBetter(Plan candidate, Plan currentBest) {
        if (currentBest == null) return true;
        int cmp = candidate.discount.compareTo(currentBest.discount);
        if (cmp != 0) return cmp > 0;
        // If same discount, prefer the one with less card spend (more points)
        BigDecimal cardSpendCandidate = candidate.cardSpend();
        BigDecimal cardSpendBest = currentBest.cardSpend();
        return cardSpendCandidate.compareTo(cardSpendBest) < 0;
    }

    private static PaymentMethod findCardWithCapacity(Collection<PaymentMethod> methods, BigDecimal amount) {
        return methods.stream()
                .filter(pm -> !"PUNKTY".equals(pm.id) && pm.hasCapacity(amount))
                .max(Comparator.comparing(pm -> pm.getRemaining())) // pick card with most remaining limit
                .orElse(null);
    }

    private static void applyPlan(Plan plan, Map<String, BigDecimal> spent) {
        for (Map.Entry<PaymentMethod, BigDecimal> e : plan.allocations.entrySet()) {
            PaymentMethod pm = e.getKey();
            BigDecimal amount = e.getValue();
            pm.spend(amount);
            spent.merge(pm.id, amount, BigDecimal::add);
        }
    }

    private record Plan(String type,
                        BigDecimal discount,
                        Map<PaymentMethod, BigDecimal> allocations) {

        BigDecimal cardSpend() {
            return allocations.entrySet().stream()
                    .filter(e -> !"PUNKTY".equals(e.getKey().id))
                    .map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
