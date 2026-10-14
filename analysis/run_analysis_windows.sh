#!/bin/bash
# Automatiza a ativação do venv, verifica pandas e executa analyze_migration.py.
# Versão para Windows usando Git Bash.
# Importante: este script espera ser executado dentro da pasta analysis/.

if [ ! -d "venv" ]; then
    echo "Virtual environment not found. Running setup first..."
    bash setup_windows.sh
else
    source venv/Scripts/activate

    if ! python -m pip freeze | grep -qi "^pandas"; then
        echo "Required packages not found. Running setup again..."
        bash setup_windows.sh
    fi
fi

if [ -z "$VIRTUAL_ENV" ]; then
    source venv/Scripts/activate
fi

echo "Running migration simulation analysis..."
python analyze_migration.py

echo -e "\nAnalysis complete! Results are available in the 'plots' directory."
echo "You can view the generated plots using your preferred image viewer."

echo -e "\nGenerated plots:"
ls -1 plots/