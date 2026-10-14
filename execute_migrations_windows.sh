#!/bin/bash
# 1) Compila o projeto Maven e copia as dependências.
# 2) Executa Best Fit e First Fit em processos separados.
# 3) Força ponto decimal nos CSVs.
# 4) Salva a quantidade de migrações em migration_counts.csv.

mvn -B compile dependency:copy-dependencies

# Executa Best Fit
BEST_FIT_OUTPUT=$(java -Duser.language=en -Duser.country=US \
  -cp "target/classes;target/dependency/*" \
  simulations.MigrationBestFitPolicy)

echo "$BEST_FIT_OUTPUT"

BEST_FIT_MIGRATIONS=$(echo "$BEST_FIT_OUTPUT" \
  | grep "Number of VM migrations:" \
  | tail -1 \
  | awk '{print $5}')

# A pasta migrations_results já terá sido criada pelo Java
echo "Strategy,Migrations" > migrations_results/migration_counts.csv
echo "best_fit,$BEST_FIT_MIGRATIONS" >> migrations_results/migration_counts.csv

# Executa First Fit
FIRST_FIT_OUTPUT=$(java -Duser.language=en -Duser.country=US \
  -cp "target/classes;target/dependency/*" \
  simulations.MigrationFirstFitPolicy)

echo "$FIRST_FIT_OUTPUT"

FIRST_FIT_MIGRATIONS=$(echo "$FIRST_FIT_OUTPUT" \
  | grep "Number of VM migrations:" \
  | tail -1 \
  | awk '{print $5}')

echo "first_fit,$FIRST_FIT_MIGRATIONS" >> migrations_results/migration_counts.csv

echo
echo "Migration counts saved to migrations_results/migration_counts.csv"
