#!/bin/bash
# 1) Compila o projeto Maven e copia as dependências.
# 2) Detecta automaticamente o separador de classpath do sistema.
# 3) Remove resultados antigos.
# 4) Descobre todas as classes Migration*Policy.
# 5) Executa cada política em um processo separado.
# 6) Força locale em inglês para manter ponto decimal nos CSVs.

set -e

echo "Compiling project..."
mvn -B compile dependency:copy-dependencies

# ------------------------------------------------------------
# Detecta o sistema operacional para definir o classpath.
#
# Windows + Git Bash -> ;
# Linux / macOS      -> :
# ------------------------------------------------------------

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        CLASSPATH_SEPARATOR=";"
        ;;
    *)
        CLASSPATH_SEPARATOR=":"
        ;;
esac

CLASSPATH="target/classes${CLASSPATH_SEPARATOR}target/dependency/*"

echo
echo "Classpath separator: $CLASSPATH_SEPARATOR"

# ------------------------------------------------------------
# Remove resultados da execução anterior.
#
# Isso evita que políticas antigas/removidas continuem
# aparecendo na análise Python.
# ------------------------------------------------------------

if [ -d "migrations_results" ]; then
    echo "Removing old migration results..."
    rm -f migrations_results/migration_*.csv migrations_results/simulation_counts.csv
fi

# ------------------------------------------------------------
# Procura e executa automaticamente todas as políticas.
#
# Convenção:
# MigrationBestFitPolicy
# MigrationFirstFitPolicy
# MigrationWorstFitPolicy
# MigrationRandomFitPolicy
# ...
# ------------------------------------------------------------

FOUND_POLICY=false
POLICY_COUNT=0

for CLASS_FILE in target/classes/simulations/Migration*Policy.class; do

    # Se nenhuma classe corresponder ao padrão,
    # o glob pode permanecer como texto literal.
    [ -e "$CLASS_FILE" ] || continue

    FOUND_POLICY=true

    CLASS_NAME=$(basename "$CLASS_FILE" .class)

    POLICY_COUNT=$((POLICY_COUNT + 1))

    echo
    echo "========================================"
    echo "Running policy: $CLASS_NAME"
    echo "========================================"

    java \
      -Duser.language=en \
      -Duser.country=US \
      -cp "$CLASSPATH" \
      "simulations.$CLASS_NAME"
done

# ------------------------------------------------------------
# Verifica se alguma política foi encontrada.
# ------------------------------------------------------------

if [ "$FOUND_POLICY" = false ]; then
    echo
    echo "No migration policies were found."
    exit 1
fi

echo
echo "========================================"
echo "All migration simulations finished."
echo "Policies executed: $POLICY_COUNT"
echo "========================================"