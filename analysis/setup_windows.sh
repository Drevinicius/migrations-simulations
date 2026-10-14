#!/bin/bash
# Recria do zero o ambiente virtual Python e instala as dependências de análise.
# Versão para Windows usando Git Bash.
# Importante: este script espera ser executado dentro da pasta analysis/.

print_message() {
    echo -e "\e[1;34m$1\e[0m"
}

# Check for Python installation
if ! command -v python &> /dev/null; then
    echo "Python is not installed or was not found in PATH."
    echo "Please install Python 3 and try again."
    exit 1
fi

mkdir -p plots

if [ -d "venv" ]; then
    print_message "Removing existing virtual environment..."
    rm -rf venv
fi

print_message "Creating Python virtual environment..."
python -m venv venv

print_message "Activating virtual environment..."
source venv/Scripts/activate

print_message "Installing required packages..."
python -m pip install --upgrade pip
python -m pip install --upgrade setuptools wheel
python -m pip install --upgrade -r requirements.txt

print_message "Setup complete! To run the analysis:"
print_message "1. Activate the virtual environment: source venv/Scripts/activate"
print_message "2. Run the analysis script: python analyze_migration.py"