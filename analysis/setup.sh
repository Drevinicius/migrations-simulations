#!/bin/bash
# 1) Detecta uma instalação válida do Python 3.
# 2) Recria o ambiente virtual.
# 3) Detecta automaticamente a estrutura do venv.
# 4) Instala as dependências do requirements.txt.
#
# Compatível com:
# - Linux
# - macOS
# - Windows + Git Bash
# - WSL

set -e

# Garante que o script funcione mesmo quando chamado
# fora da pasta analysis/.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

print_message() {
    echo
    echo "==> $1"
}

# ------------------------------------------------------------
# Detecta o comando Python.
# ------------------------------------------------------------

if command -v python3 >/dev/null 2>&1; then
    PYTHON_CMD="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_CMD="python"
else
    echo "Python 3 was not found in PATH."
    echo "Please install Python 3 and try again."
    exit 1
fi

# Confirma que realmente é Python 3.
if ! "$PYTHON_CMD" -c "import sys; sys.exit(0 if sys.version_info.major >= 3 else 1)"; then
    echo "Python 3 is required."
    exit 1
fi

print_message "Using Python: $PYTHON_CMD"
"$PYTHON_CMD" --version

mkdir -p plots

# ------------------------------------------------------------
# Recria o ambiente virtual.
# ------------------------------------------------------------

if [ -d "venv" ]; then
    print_message "Removing existing virtual environment..."
    rm -rf venv
fi

print_message "Creating Python virtual environment..."
"$PYTHON_CMD" -m venv venv

# ------------------------------------------------------------
# Detecta onde o Python do venv foi criado.
#
# Windows -> venv/Scripts/python.exe
# Unix    -> venv/bin/python
# ------------------------------------------------------------

if [ -f "venv/Scripts/python.exe" ]; then
    VENV_PYTHON="venv/Scripts/python.exe"
elif [ -f "venv/bin/python" ]; then
    VENV_PYTHON="venv/bin/python"
else
    echo "Could not locate Python inside the virtual environment."
    exit 1
fi

# ------------------------------------------------------------
# Instala as dependências usando diretamente o Python do venv.
# ------------------------------------------------------------

print_message "Installing required packages..."

"$VENV_PYTHON" -m pip install --upgrade pip
"$VENV_PYTHON" -m pip install --upgrade setuptools wheel
"$VENV_PYTHON" -m pip install --upgrade -r requirements.txt

print_message "Setup complete."
echo "Run the analysis with:"
echo "./run_analysis.sh"