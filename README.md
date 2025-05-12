# Payment Optimizer

Narzędzie wiersza poleceń, które dobiera optymalny sposób opłacenia każdego zamówienia, maksymalizując sumaryczny rabat i pilnując limitów metod płatności.

## Wymagania

* **Java 17**  
* **Maven 3.9** lub nowszy

## Budowanie

```bash
mvn clean package
```

Maven utworzy w `target/` samodzielny plik JAR (*fat jar*) `payment-optimizer-1.0-SNAPSHOT.jar`.

## Uruchamianie

```bash
java -jar target/payment-optimizer-1.0-SNAPSHOT.jar orders.json paymentmethods.json
```

Program wypisze, ile **netto** (po rabatach) zapłacono każdą metodą, np.:

```
mZysk 165.00
BosBankrut 190.00
PUNKTY  57.50
```

## Jak to działa (skrót)

1. Wczytuje zamówienia i metody płatności (Jackson).  
2. Dla każdego zamówienia rozważa – w podanej kolejności – cztery wykluczające się plany:  
   * 100 % punktami (15 % rabatu),  
   * 100 % kartą z promocją > 10 %,  
   * **split** – ≥ 10 % punktami + reszta jedną kartą (rabat 10 %),  
   * karta bez promocji (0 % rabatu).  
3. Wybiera pierwszy możliwy plan; limitom odejmuje **kwoty po rabacie**.  
4. Złożoność `O(n·k)` – szybka przy limitach z treści zadania.

## Testy

```bash
mvn test
```

Uruchamia pakiet testów jednostkowych sprawdzających logikę wyboru planów.
