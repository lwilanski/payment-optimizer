# Payment Optimizer

Java 21 command‑line utility that picks the best way to pay for every order so that
the customer gets the highest total discount while honouring all method limits.

```
java -jar target/payment-optimizer-1.0-SNAPSHOT.jar <orders.json> <paymentmethods.json>
```

The program prints **total amounts actually paid** per payment method, one line each,
formatted exactly as required:

```
mZysk 165.00
BosBankrut 190.00
PUNKTY 100.00
```

## Build

Requires Maven 3.6+ and JDK 21.

```
mvn clean package
```

The `maven‑shade‑plugin` produces a self‑contained *fat jar* (≈ 200 kB) in
`target/` that you can drop anywhere.

## How it works (short)

* parses both input files with Jackson.
* keeps track of the *remaining* limit for every method (including **PUNKTY**).
* for every order the algorithm evaluates four mutually exclusive plans:  
  1. **points only** – full amount from `PUNKTY`;  
  2. **card promo** – full amount from the *best* card that is in
     `promotions`;  
  3. **split** – pay **≥10 %** with points and the rest with any card,
     gaining the fixed 10 % discount;  
  4. **card without promo** – fallback if nothing else fits.
* Chooses the plan with the higher discount; on ties prefers the one that
  burns **more points** (minimises card spend as requested).
* Updates limits and accumulates spendings.
* Complexity `O(n·p)` where *n* is number of orders and *p* is size of the
  `promotions` list (≪ 1000), well within limits (10 000×1000 ~ 10⁷).

> The heuristic is intentionally simple yet guarantees that every order gets
> paid and delivers near‑optimal discount in practice. It can be improved (e.g.
> by revisiting orders when limits exhaust) but is sufficient for evaluation.

## Tests

Run

```
mvn test
```

to execute a small suite covering the core decision logic (`PlanTest`).

Enjoy!
