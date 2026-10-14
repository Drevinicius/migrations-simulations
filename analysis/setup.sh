#!/bin/bash
# Recria do zero o ambiente virtual Python e instala as dependências de análise.
# Importante: este script espera ser executado dentro da pasta analysis/.

print_message() {
    echo -e "\e[1;34m$1\e[0m"
}

# Check for Python installation
if ! command -v python3 &> /dev/null; then
    echo "Python 3 is not installed. Please install Python 3 and try again."
    exit 1
fi

mkdir -p plots

if [ -d "venv" ]; then
    print_message "Removing existing virtual environment..."
    rm -rf venv
fi

print_message "Creating Python virtual environment..."
python3 -m venv venv

print_message "Activating virtual environment..."
source venv/bin/activate

print_message "Installing required packages..."
pip install --upgrade pip
pip install --upgrade setuptools wheel
pip install --upgrade -r requirements.txt

print_message "Setup complete! To run the analysis:"
print_message "1. Activate the virtual environment: source venv/bin/activate"
print_message "2. Run the analysis script: python3 analyze_migration.py"
