# Wycofanie migracji

Flyway Community nie ma polecenia `undo`, więc każda migracja ma tu skrypt wycofania `U<wersja>__<nazwa>.sql` z tą samą wersją. Skrypty usuwają obiekty i wiersz z historii Flyway; są powtarzalne.

Uruchamianie na węźle (przykład dla MasterCard):

```bash
docker exec -i mastercard-db bash -c 'sqlplus -s "bramka/\"$BRAMKA_PASSWORD\"@//localhost/FREEPDB1"' < database/wycofanie/fragmenty/U3__pakiety_autoryzacji_i_kart.sql
```

Kolejność: od najnowszej wersji do najstarszej, najpierw `visa-polaczenia` na węźle Visa, potem węzły MC i AmEx (`fragmenty`, `repliki`), na końcu węzeł Visa (`visa`, `fragmenty`).

Po wycofaniu migracji i usunięciu jej pliku z repozytorium kolejne `docker compose up` nie zastosuje jej ponownie. Gdy plik zostaje, `docker compose up` odtworzy obiekty od nowa.
