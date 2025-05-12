package com.example.paymentoptimizer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class PaymentOptimizer {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java -jar app.jar <orders.json> <paymentmethods.json>");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Order> orders  = mapper.readValue(new File(args[0]), new TypeReference<>() {});
        List<PaymentMethod> methods =
                mapper.readValue(new File(args[1]), new TypeReference<>() {});

        Map<String,PaymentMethod> byId = new HashMap<>();
        for (PaymentMethod pm : methods) byId.put(pm.id, pm);

        PaymentMethod points = byId.get("PUNKTY");
        if (points == null) { System.err.println("Missing PUNKTY"); System.exit(1); }

        orders.sort(Comparator.comparing(Order::value).reversed()); // duże najpierw
        Map<String,BigDecimal> spent = new LinkedHashMap<>();

        for (Order ord : orders) {
            Plan plan = choosePlan(ord, points, byId);
            apply(plan, spent);
        }

        spent.forEach((id,net) ->
                System.out.printf(Locale.US,"%s %.2f%n", id, net.setScale(2,RoundingMode.HALF_UP)));
    }

    // --------------------------------------------------------------------
    private static Plan choosePlan(Order order,
                                   PaymentMethod points,
                                   Map<String,PaymentMethod> methods) {

        BigDecimal gross = order.value();

        // 1) karta promocyjna >10 %
        PaymentMethod promo = order.promotions().stream()
                .map(methods::get)
                .filter(Objects::nonNull)
                .filter(pm -> pm.discountPercent > 10
                           && pm.hasCapacity(net(gross, pm.discountPercent)))
                .max(Comparator.comparingInt(pm -> pm.discountPercent))
                .orElse(null);

        if (promo != null) {
            return new Plan("CARD_PROMO",
                    percent(gross, promo.discountPercent),
                    Map.of(promo, net(gross, promo.discountPercent)));
        }

        // 2a) 100 % punktów (15 %)
        if (points.hasCapacity(net(gross, points.discountPercent))) {
            return new Plan("POINTS_ONLY",
                    percent(gross, points.discountPercent),
                    Map.of(points, net(gross, points.discountPercent)));
        }

        // 2b) split ≥10 % punktów + karta (rabat 10 %)
        PaymentMethod richest = cardWithMaxLimit(methods.values());
        if (richest != null) {
            BigDecimal capNet      = richest.getRemaining();            // limit netto karty
            BigDecimal tenPctGross = ceil2(percent(gross, 10));         // ≥10 % punkty (brutto)

            BigDecimal cardGrossMax = capNet.multiply(HUNDRED)
                                            .divide(BigDecimal.valueOf(90), 2, RoundingMode.FLOOR);

            BigDecimal pointsGross  = ceil2(gross.subtract(cardGrossMax).max(tenPctGross));
            BigDecimal cardGross    = gross.subtract(pointsGross);

            BigDecimal pointsNet = net(pointsGross,10);
            BigDecimal cardNet   = net(cardGross, 10);

            if (richest.hasCapacity(cardNet) && points.hasCapacity(pointsNet)
                    && pointsGross.compareTo(gross) < 0) {

                Map<PaymentMethod,BigDecimal> alloc = new LinkedHashMap<>();
                alloc.put(points,   pointsNet);
                alloc.put(richest,  cardNet);

                return new Plan("SPLIT", percent(gross,10), alloc);
            }
        }

        // 2c) dowolna karta bez rabatu
        PaymentMethod any = firstCardWithLimit(methods.values(), net(gross,0));
        if (any != null) {
            return new Plan("CARD_NO_PROMO", BigDecimal.ZERO,
                    Map.of(any, net(gross,0)));
        }

        throw new IllegalStateException("Unable to pay for order " + order.id());
    }

    private static void apply(Plan plan, Map<String,BigDecimal> spent) {
        for (var e : plan.allocations.entrySet()) {
            e.getKey().spend(e.getValue());
            spent.merge(e.getKey().id, e.getValue(), BigDecimal::add);
        }
    }

    private static BigDecimal percent(BigDecimal base,int pct){
        return base.multiply(BigDecimal.valueOf(pct)).divide(HUNDRED);
    }
    private static BigDecimal net(BigDecimal gross,int discPct){
        return gross.multiply(BigDecimal.valueOf(100-discPct)).divide(HUNDRED);
    }
    private static BigDecimal ceil2(BigDecimal x){
        return x.setScale(2,RoundingMode.CEILING);
    }
    private static PaymentMethod cardWithMaxLimit(Collection<PaymentMethod> ms){
        return ms.stream().filter(pm->!"PUNKTY".equals(pm.id))
                 .max(Comparator.comparing(PaymentMethod::getRemaining)).orElse(null);
    }
    private static PaymentMethod firstCardWithLimit(Collection<PaymentMethod> ms,BigDecimal needNet){
        return ms.stream().filter(pm->!"PUNKTY".equals(pm.id)&&pm.hasCapacity(needNet))
                 .findFirst().orElse(null);
    }

    private record Plan(String type, BigDecimal discount,
                        Map<PaymentMethod,BigDecimal> allocations){}
}
