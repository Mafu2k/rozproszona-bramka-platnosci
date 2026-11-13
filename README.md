# rozproszona-bramka-platnosci


O projekcie

Rozproszona baza danych bramki płatności internetowych z symulowaną autoryzacją kart oraz aplikacja w Javie, która z niej korzysta.


| Węzeł | Sieć | Fragmenty lokalne | Repliki |
|:---:|---|---|---|
| `1` `visa` | Visa | Karta i transakcja Visa | Oryginał tabel: klient, sklep, sieć kartowa, waluta |
| `2` `mastercard` | MasterCard | Karta i transakcja MasterCard | Widoki zmaterializowane: klient, sklep, sieć kartowa, waluta |
| `3` `amex` | American Express | Karta i transakcja American Express | Widoki zmaterializowane: klient, sklep, sieć kartowa, waluta |