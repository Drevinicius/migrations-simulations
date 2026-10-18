#!/bin/bash
# 1) Localiza o ambiente virtual.
# 2) Executa o setup automaticamente se necessário.
# 3) Verifica as dependências.
# 4) Executa analyze_migration.py.
#
# Compatível com:
# - Linux
# - macOS
# - Windows + Git Bash
# - WSL

set -e

# Permite executar o script de qualquer diretório.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ------------------------------------------------------------
# Localiza o Python dentro do venv.
# ------------------------------------------------------------

find_venv_python() {
    if [ -f "venv/Scripts/python.exe" ]; then
        VENV_PYTHON="venv/Scripts/python.exe"
        return 0
    fi

    if [ -f "venv/bin/python" ]; then
        VENV_PYTHON="venv/bin/python"
        return 0
    fi

    return 1
}

# ------------------------------------------------------------
# Cria o ambiente caso ainda não exista.
# ------------------------------------------------------------

if ! find_venv_python; then
    echo "Virtual environment not found."
    echo "Running setup..."
    bash setup.sh

    if ! find_venv_python; then
        echo "Failed to create virtual environment."
        exit 1
    fi
fi

# ------------------------------------------------------------
# Verifica se as bibliotecas necessárias estão instaladas.
# É melhor testar os imports do que procurar apenas pandas
# no resultado do pip freeze.
# ------------------------------------------------------------

if ! "$VENV_PYTHON" -c "import pandas, matplotlib, seaborn" >/dev/null 2>&1; then
    echo "Required Python packages are missing."
    echo "Running setup again..."

    bash setup.sh

    if ! find_venv_python; then
        echo "Failed to configure virtual environment."
        exit 1
    fi
fi

# ------------------------------------------------------------
# Executa a análise.
# ------------------------------------------------------------

echo
echo "========================================"
echo "Running migration simulation analysis..."
echo "========================================"

"$VENV_PYTHON" analyze_migration.py

echo
echo "========================================"
echo "Analysis completed successfully."
echo "========================================"

echo
echo "Generated files:"

if [ -d "plots" ]; then
    ls -1 plots/ 2>/dev/null || true
else
    echo "No plots directory was generated."
fi