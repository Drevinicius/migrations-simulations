# ---------------------------------------------------------------------------
# LEITURA DIDÁTICA
# Este arquivo NÃO executa a simulação. Ele apenas lê os CSVs gerados pelo Java,
# calcula estatísticas e cria gráficos/tabela comparativa.
# ---------------------------------------------------------------------------
import os
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from pathlib import Path

plt.style.use('ggplot')
sns.set_palette("colorblind")

# Carrega todos os migration_*.csv. Arquivos *_power têm CSV comum;
# arquivos *_policy vêm do CloudletsTableBuilder e possuem uma segunda linha
# de unidades, por isso o skiprows=2.
def load_data(migration_results_dir='../migrations_results'):
    """Load all CSV files with migration data from migrations_results directory"""
    data_files = {}
    results_path = Path(migration_results_dir)

    for file_path in results_path.glob('migration_*.csv'):
        strategy_name = file_path.stem.replace('migration_', '')
        if 'power' in strategy_name:
            df = pd.read_csv(file_path)
            data_files[strategy_name] = df
        elif 'policy' in strategy_name:
            # For policy files, we need to skip the first row and fix column names
            # First read the header to get column names
            with open(file_path, 'r') as f:
                header_line = f.readline().strip()

            # Clean up column names - they have a specific format with commas and spaces
            column_names = [col.strip() for col in header_line.split(',')]

            # Now read the data, skipping the first row (column names) and the second row (units)
            df = pd.read_csv(file_path, skiprows=2, names=column_names)
            data_files[strategy_name] = df

    return data_files

# Resume potência/energia por estratégia. "active_hosts" aqui significa
# CPUUtilization > 0 no CSV; não significa necessariamente Host fisicamente ligado.
def analyze_power_consumption(power_data):
    """Analyze power consumption data from different strategies"""
    results = {}

    for strategy, df in power_data.items():
        if 'PowerConsumption(W)' in df.columns:
            total_row = df[df['Host'] == 'Total']

            if total_row.empty:
                active_hosts = df[df['CPUUtilization'] > 0].shape[0]
                total_power = df['PowerConsumption(W)'].sum()
                avg_power = df['PowerConsumption(W)'].mean()
                max_power = df['PowerConsumption(W)'].max()
                total_energy = df['TotalEnergyConsumption(Wh)'].sum()
            else:
                total_power = total_row['PowerConsumption(W)'].values[0]
                total_energy = total_row['TotalEnergyConsumption(Wh)'].values[0]

                df_without_total = df[df['Host'] != 'Total']
                active_hosts = df_without_total[df_without_total['CPUUtilization'] > 0].shape[0]
                avg_power = df_without_total['PowerConsumption(W)'].mean()
                max_power = df_without_total['PowerConsumption(W)'].max()

            results[strategy] = {
                'total_power_consumption': total_power,
                'average_power_consumption': avg_power,
                'max_power_consumption': max_power,
                'active_hosts': active_hosts,
                'total_energy_consumption': total_energy
            }

    return results

# Gera o gráfico que efetivamente compara energia total entre estratégias.
def plot_power_comparison(power_data):
    """Create comparative plots for power consumption across strategies"""
    power_dfs = {k: v for k, v in power_data.items() if 'power' in k}

    if not power_dfs:
        print("No power data found to plot")
        return

    os.makedirs('plots', exist_ok=True)

    plt.figure(figsize=(10, 6))
    results = analyze_power_consumption(power_dfs)

    strategies = [s.replace('_power', '') for s in results.keys()]
    total_energy = [results[s]['total_energy_consumption'] for s in results.keys()]

    plt.bar(strategies, total_energy)
    plt.title('Total Energy Consumption by Migration Strategy')
    plt.xlabel('Migration Strategy')
    plt.ylabel('Total Energy Consumption (Wh)')
    plt.tight_layout()
    plt.savefig('plots/total_energy_comparison.png')

    print(f"Total energy comparison plot saved to the 'plots' directory")

    return results

# Função auxiliar de distribuição. Observação semântica: df.shape[0] conta
# linhas de Cloudlets; neste cenário coincide com VMs porque há 1 Cloudlet/VM.
def analyze_migration_policies(policy_data):
    """Analyze migration policy data"""
    results = {}

    for strategy, df in policy_data.items():
        if 'policy' in strategy:
            total_vms = df.shape[0]
            avg_exec_time = df['ExecTime'].mean() if 'ExecTime' in df.columns else 0

            if 'Host' in df.columns and 'VM' in df.columns:
                vm_per_host = df.groupby('Host').size()
                max_vms_per_host = vm_per_host.max()
                min_vms_per_host = vm_per_host.min() if not vm_per_host.empty else 0
            else:
                max_vms_per_host = min_vms_per_host = 0

            results[strategy] = {
                'total_vms': total_vms,
                'average_execution_time': avg_exec_time,
                'max_vms_per_host': max_vms_per_host,
                'min_vms_per_host': min_vms_per_host
            }

    return results

# Também conta linhas por Host. Com 1 Cloudlet/VM, equivale à distribuição de VMs.
def plot_vm_distribution(policy_data):
    """Plot VM distribution across hosts"""
    os.makedirs('plots', exist_ok=True)

    policy_dfs = {k: v for k, v in policy_data.items() if 'policy' in k}

    if not policy_dfs:
        print("No policy data found to plot")
        return

    for strategy, df in policy_dfs.items():
        strategy_name = strategy.replace('_policy', '')
        if 'Host' in df.columns:
            plt.figure(figsize=(12, 6))

            vm_counts = df.groupby('Host').size().sort_values(ascending=False)

            vm_counts.plot(kind='bar')
            plt.title(f'VM Distribution Across Hosts ({strategy_name})')
            plt.xlabel('Host ID')
            plt.ylabel('Number of VMs')
            plt.tight_layout()
            plt.savefig(f'plots/vm_distribution_{strategy_name}.png')

    print(f"VM distribution plots saved to the 'plots' directory")

# Procura dinamicamente a coluna que contém "Exec" e "Time" para tolerar
# pequenas mudanças no cabeçalho produzido pelo CloudletsTableBuilder.
def analyze_execution_times(policy_data):
    """Analyze execution times of cloudlets across different strategies"""
    results = {}

    for strategy, df in policy_data.items():
        if 'policy' in strategy:
            strategy_name = strategy.replace('_policy', '')

            # Check if we have the execution time column
            # It might be named 'ExecTime' or have spaces like 'Exec Time'
            exec_time_col = None
            for col in df.columns:
                if 'Exec' in col and 'Time' in col:
                    exec_time_col = col
                    break

            if exec_time_col is None:
                print(f"Warning: No execution time column found in {strategy}")
                continue

            # Convert to numeric, handling any non-numeric values
            df[exec_time_col] = pd.to_numeric(df[exec_time_col], errors='coerce')

            # Calculate execution time statistics
            min_time = df[exec_time_col].min()
            max_time = df[exec_time_col].max()
            avg_time = df[exec_time_col].mean()
            total_time = df[exec_time_col].sum()

            results[strategy_name] = {
                'min_execution_time': min_time,
                'max_execution_time': max_time,
                'avg_execution_time': avg_time,
                'total_execution_time': total_time,
                'cloudlet_count': len(df)
            }

    return results

def plot_execution_time_comparison(policy_data):
    """Create comparative plot for maximum execution time across strategies"""
    policy_dfs = {k: v for k, v in policy_data.items() if 'policy' in k}

    if not policy_dfs:
        print("No policy data found to plot execution time comparison")
        return

    os.makedirs('plots', exist_ok=True)

    # Get execution time statistics
    exec_time_stats = analyze_execution_times(policy_data)

    # Convert to DataFrame for easier plotting
    stats_df = pd.DataFrame(exec_time_stats).T

    # Create bar chart for max execution time
    plt.figure(figsize=(10, 6))
    strategies = list(stats_df.index)
    max_times = [stats_df.loc[s, 'max_execution_time'] for s in strategies]

    plt.bar(strategies, max_times)
    plt.title('Maximum Execution Time by Migration Strategy')
    plt.xlabel('Migration Strategy')
    plt.ylabel('Maximum Execution Time (seconds)')
    plt.tight_layout()
    plt.savefig('plots/max_execution_time_comparison.png')

    print(f"Maximum execution time comparison plot saved to the 'plots' directory")

    return exec_time_stats

def plot_total_execution_time_comparison(policy_data):
    """Create comparative plot for total execution time across strategies"""
    policy_dfs = {k: v for k, v in policy_data.items() if 'policy' in k}

    if not policy_dfs:
        print("No policy data found to plot total execution time comparison")
        return

    os.makedirs('plots', exist_ok=True)

    # Get execution time statistics
    exec_time_stats = analyze_execution_times(policy_data)

    # Convert to DataFrame for easier plotting
    stats_df = pd.DataFrame(exec_time_stats).T

    # Create bar chart for total execution time
    plt.figure(figsize=(10, 6))
    strategies = list(stats_df.index)
    total_times = [stats_df.loc[s, 'total_execution_time'] for s in strategies]

    plt.bar(strategies, total_times)
    plt.title('Total Execution Time by Migration Strategy')
    plt.xlabel('Migration Strategy')
    plt.ylabel('Total Execution Time (seconds)')
    plt.tight_layout()
    plt.savefig('plots/total_execution_time_comparison.png')

    print(f"Total execution time comparison plot saved to the 'plots' directory")

    return exec_time_stats

# Junta métricas de energia e execução em um único CSV comparativo.
def generate_comparison_table(power_results, exec_time_results=None):
    """Generate a comparison table for all strategies"""
    comparison_data = []

    for strategy, results in power_results.items():
        strategy_name = strategy.replace('_power', '')

        # Start with power results
        strategy_data = {
            'Strategy': strategy_name,
            'Total Power (W)': results['total_power_consumption'],
            'Average Power (W)': results['average_power_consumption'],
            'Total Energy (Wh)': results['total_energy_consumption'],
            'Active Hosts': results['active_hosts']
        }

        # Add execution time results if available
        if exec_time_results and strategy_name in exec_time_results:
            exec_results = exec_time_results[strategy_name]
            strategy_data.update({
                'Total Exec Time (s)': exec_results['total_execution_time'],
                'Avg Exec Time (s)': exec_results['avg_execution_time'],
                'Max Exec Time (s)': exec_results['max_execution_time'],
                'Min Exec Time (s)': exec_results['min_execution_time'],
                'Cloudlet Count': exec_results['cloudlet_count']
            })

        comparison_data.append(strategy_data)

    comparison_df = pd.DataFrame(comparison_data)
    comparison_df.to_csv('plots/complete_strategy_comparison.csv', index=False)

    print("\nComplete Strategy Comparison:")
    print(comparison_df.to_string(index=False))

# Pipeline: carregar -> separar power/policy -> gráficos -> tabela final.
def main():
    print("Starting migration simulation analysis...")

    data = load_data()

    power_data = {k: v for k, v in data.items() if 'power' in k}
    policy_data = {k: v for k, v in data.items() if 'policy' in k}

    os.makedirs('plots', exist_ok=True)

    exec_time_results = None
    power_results = None

    if power_data:
        print("\nAnalyzing power consumption data...")
        power_results = plot_power_comparison(power_data)

        for strategy, results in power_results.items():
            strategy_name = strategy.replace('_power', '')
            print(f"\n{strategy_name} power statistics:")
            for metric, value in results.items():
                print(f"  {metric}: {value:.2f}")
    else:
        print("No power consumption data found")

    if policy_data:
        print("\nAnalyzing execution time data...")
        exec_time_results = plot_execution_time_comparison(policy_data)

        # Add total execution time plot
        plot_total_execution_time_comparison(policy_data)

        for strategy, results in exec_time_results.items():
            print(f"\n{strategy} execution time statistics:")
            for metric, value in results.items():
                print(f"  {metric}: {value:.4f}")
    else:
        print("No migration policy data found")

    if power_data and policy_data:
        print("\nGenerating comprehensive comparison table...")
        generate_comparison_table(power_results, exec_time_results)

if __name__ == "__main__":
    main()
