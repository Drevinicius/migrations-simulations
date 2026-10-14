#!/bin/bash
# Automatiza a ativação do venv, verifica pandas e executa analyze_migration.py.

if [ ! -d "venv" ]; then
    echo "Virtual environment not found. Running setup first..."
    ./setup.sh
else
    source venv/bin/activate

    if ! pip freeze | grep -q "pandas"; then
        echo "Required packages not found. Running setup again..."
        ./setup.sh
    fi
fi

if [ -z "$VIRTUAL_ENV" ]; then
    source venv/bin/activate
fi

echo "Running migration simulation analysis..."
python3 analyze_migration.py

echo -e "\nAnalysis complete! Results are available in the 'plots' directory."
echo "You can view the generated plots using your preferred image viewer."

echo -e "\nGenerated plots:"
ls -1 plots/
