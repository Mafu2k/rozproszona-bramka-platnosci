DROP PACKAGE IF EXISTS pkg_karty;
DROP PACKAGE IF EXISTS pkg_autoryzacja;
DELETE FROM "flyway_schema_history" WHERE "version" = '3';
COMMIT;
