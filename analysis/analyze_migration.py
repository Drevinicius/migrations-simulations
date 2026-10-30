import os
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns


plt.style.use('ggplot')
sns.set_palette("colorblind")


# ---------------------------------------------------------------------------
# CAMINHOS
# ---------------------------------------------------------------------------

BASE_DIR = Path(__file__).resolve().parent
RESULTS_DIR = BASE_DIR.parent / 'migrations_results'
PLOTS_DIR = BASE_DIR / 'plots'


# ---------------------------------------------------------------------------
# DESCOBERTA E CARREGAMENTO DAS ESTRATÉGIAS
# ---------------------------------------------------------------------------

def load_policy_csv(file_path):
    """
    Carrega um CSV gerado pelo CloudletsTableBuilder.

    Esses arquivos possuem:
    - linha 1: nomes das colunas;
    - linha 2: unidades;
    - demais linhas: dados das Cloudlets.
    """

    with open(file_path, 'r') as file:
        header_line = file.readline().strip()

    column_names = [
        column.strip()
        for column in header_line.split(',')
    ]

    return pd.read_csv(
        file_path,
        skiprows=2,
        names=column_names
    )


def load_migration_counts(file_path):
    """
    Carrega migration_counts.csv e normaliza o formato.

    Formato atual esperado:

    Strategy,
    PlannedMigrations,
    StartedMigrations,
    CompletedMigrations,
    SuccessfulMigrations,
    FailedMigrations,
    NotStartedMigrations,
    UnfinishedMigrations

    Compatibilidade mantida com resultados antigos:
    - Migrations -> StartedMigrations;
    - arquivos sem PlannedMigrations/NotStartedMigrations continuam válidos;
    - métricas de planejamento ausentes permanecem como dados desconhecidos,
      em vez de serem assumidas como zero ou iguais às migrações iniciadas.
    """

    df = pd.read_csv(file_path)

    # Compatibilidade com o CSV antigo.
    if (
        'StartedMigrations' not in df.columns
        and 'Migrations' in df.columns
    ):
        df = df.rename(
            columns={
                'Migrations': 'StartedMigrations'
            }
        )

    required_numeric_columns = [
        'StartedMigrations',
        'CompletedMigrations',
        'SuccessfulMigrations',
        'FailedMigrations',
        'UnfinishedMigrations'
    ]

    optional_numeric_columns = [
        'PlannedMigrations',
        'NotStartedMigrations'
    ]

    for column in required_numeric_columns:
        if column in df.columns:
            df[column] = pd.to_numeric(
                df[column],
                errors='coerce'
            ).fillna(0).astype(int)

    # Para dados históricos, não converte ausência de planejamento em zero.
    for column in optional_numeric_columns:
        if column in df.columns:
            df[column] = pd.to_numeric(
                df[column],
                errors='coerce'
            ).astype('Int64')

    # Compatibilidade caso o CSV ainda não possua CompletedMigrations.
    if (
        'CompletedMigrations' not in df.columns
        and 'SuccessfulMigrations' in df.columns
        and 'FailedMigrations' in df.columns
    ):
        df['CompletedMigrations'] = (
            df['SuccessfulMigrations']
            + df['FailedMigrations']
        )

    required_columns = [
        'Strategy',
        'StartedMigrations',
        'CompletedMigrations',
        'SuccessfulMigrations',
        'FailedMigrations',
        'UnfinishedMigrations'
    ]

    missing_columns = [
        column
        for column in required_columns
        if column not in df.columns
    ]

    if missing_columns:
        print(
            'Warning: migration_counts.csv is missing columns: '
            + ', '.join(missing_columns)
        )

        return pd.DataFrame()

    return df


def load_simulation_counts(file_path):
    """
    Carrega simulation_counts.csv.

    O arquivo é gerado pelo Java ao final de cada estratégia e contém:

        Strategy,SubmittedCloudlets,FinishedCloudlets

    Esses valores representam o estado real mantido pelo Broker e são
    utilizados para validar se todas as Cloudlets submetidas terminaram.
    """

    df = pd.read_csv(file_path)

    required_columns = [
        'Strategy',
        'SubmittedCloudlets',
        'FinishedCloudlets'
    ]

    missing_columns = [
        column
        for column in required_columns
        if column not in df.columns
    ]

    if missing_columns:
        print(
            'Warning: simulation_counts.csv is missing columns: '
            + ', '.join(missing_columns)
        )
        return pd.DataFrame()

    for column in [
        'SubmittedCloudlets',
        'FinishedCloudlets'
    ]:
        df[column] = pd.to_numeric(
            df[column],
            errors='coerce'
        ).fillna(0).astype(int)

    return df


def discover_strategies(
        migration_results_dir=RESULTS_DIR):
    """
    Descobre automaticamente todas as estratégias disponíveis.

    Convenção de arquivos:

        migration_<strategy>_policy.csv
        migration_<strategy>_power.csv

    Exemplo:

        migration_best_fit_policy.csv
            -> strategy = best_fit

        migration_worst_fit_power.csv
            -> strategy = worst_fit

    O resultado possui a estrutura:

        {
            'best_fit': {
                'policy': DataFrame,
                'power': DataFrame,
                'migrations': {...}
            }
        }
    """

    strategies = {}

    results_path = Path(migration_results_dir)

    print(f'Caminho absoluto verificado: {results_path}')
    print(f'A pasta existe? {results_path.exists()}')

    if not results_path.exists():
        print('Migration results directory not found.')
        return strategies

    # ------------------------------------------------------------------
    # Descobre arquivos de policy e power.
    # ------------------------------------------------------------------

    for file_path in results_path.glob('migration_*.csv'):

        filename = file_path.stem

        # migration_counts.csv é tratado separadamente.
        if filename == 'migration_counts':
            continue

        strategy_name = None
        data_type = None

        # --------------------------------------------------------------
        # migration_<strategy>_policy.csv
        # --------------------------------------------------------------
        if (
            filename.startswith('migration_')
            and filename.endswith('_policy')
        ):
            strategy_name = filename[
                len('migration_'):
                -len('_policy')
            ]

            data_type = 'policy'

        # --------------------------------------------------------------
        # migration_<strategy>_power.csv
        # --------------------------------------------------------------
        elif (
            filename.startswith('migration_')
            and filename.endswith('_power')
        ):
            strategy_name = filename[
                len('migration_'):
                -len('_power')
            ]

            data_type = 'power'

        else:
            print(
                f'Warning: unrecognized migration file ignored: '
                f'{file_path.name}'
            )
            continue

        if not strategy_name:
            continue

        # Cria a estratégia caso ainda não exista.
        strategies.setdefault(
            strategy_name,
            {}
        )

        # --------------------------------------------------------------
        # Carrega os dados.
        # --------------------------------------------------------------
        if data_type == 'policy':

            print(
                f'Loading policy data for strategy: '
                f'{strategy_name}'
            )

            strategies[strategy_name]['policy'] = (
                load_policy_csv(file_path)
            )

        elif data_type == 'power':

            print(
                f'Loading power data for strategy: '
                f'{strategy_name}'
            )

            strategies[strategy_name]['power'] = (
                pd.read_csv(file_path)
            )

    # ------------------------------------------------------------------
    # Carrega migration_counts.csv.
    # ------------------------------------------------------------------

    counts_file = (
        results_path
        / 'migration_counts.csv'
    )

    if counts_file.exists():

        print('Loading migration counts...')

        counts_df = load_migration_counts(
            counts_file
        )

        if not counts_df.empty:

            for _, row in counts_df.iterrows():

                strategy_name = str(
                    row['Strategy']
                ).strip()

                if not strategy_name:
                    continue

                # Uma estratégia pode existir apenas no migration_counts.csv.
                strategies.setdefault(
                    strategy_name,
                    {}
                )

                planned = None
                not_started = None

                if (
                    'PlannedMigrations' in counts_df.columns
                    and pd.notna(row['PlannedMigrations'])
                ):
                    planned = int(
                        row['PlannedMigrations']
                    )

                if (
                    'NotStartedMigrations' in counts_df.columns
                    and pd.notna(row['NotStartedMigrations'])
                ):
                    not_started = int(
                        row['NotStartedMigrations']
                    )

                started = int(
                    row['StartedMigrations']
                )

                completed = int(
                    row['CompletedMigrations']
                )

                successful = int(
                    row['SuccessfulMigrations']
                )

                failed = int(
                    row['FailedMigrations']
                )

                unfinished = int(
                    row['UnfinishedMigrations']
                )

                lifecycle_consistent = (
                    started
                    == completed + unfinished
                    and completed
                    == successful + failed
                )

                planning_consistent = (
                    planned is None
                    or not_started is None
                    or planned == started + not_started
                )

                if not (
                    lifecycle_consistent
                    and planning_consistent
                ):
                    print(
                        'Warning: inconsistent migration counters '
                        f"for strategy '{strategy_name}'."
                    )

                strategies[
                    strategy_name
                ]['migrations'] = {
                    'planned': planned,
                    'started': started,
                    'completed': completed,
                    'successful': successful,
                    'failed': failed,
                    'not_started': not_started,
                    'unfinished': unfinished
                }

    else:
        print(
            'Warning: migration_counts.csv not found.'
        )

    # ------------------------------------------------------------------
    # Carrega simulation_counts.csv.
    # ------------------------------------------------------------------

    simulation_counts_file = (
        results_path
        / 'simulation_counts.csv'
    )

    if simulation_counts_file.exists():

        print('Loading simulation cloudlet counts...')

        simulation_counts_df = load_simulation_counts(
            simulation_counts_file
        )

        if not simulation_counts_df.empty:

            for _, row in simulation_counts_df.iterrows():

                strategy_name = str(
                    row['Strategy']
                ).strip()

                if not strategy_name:
                    continue

                # A estratégia também pode ser descoberta apenas por este CSV.
                strategies.setdefault(
                    strategy_name,
                    {}
                )

                strategies[
                    strategy_name
                ]['cloudlets'] = {
                    'submitted': int(
                        row['SubmittedCloudlets']
                    ),
                    'finished': int(
                        row['FinishedCloudlets']
                    )
                }

    else:
        print(
            'Warning: simulation_counts.csv not found.'
        )

    if not strategies:
        print(
            'No migration strategies were discovered.'
        )

    return strategies


# ---------------------------------------------------------------------------
# CONSUMO DE ENERGIA
# ---------------------------------------------------------------------------

def analyze_power_consumption(df):
    """
    Analisa o CSV de consumo energético de uma estratégia.
    """

    if (
        df is None
        or df.empty
        or 'PowerConsumption(W)' not in df.columns
    ):
        return None

    total_row = df[
        df['Host'] == 'Total'
    ]

    if total_row.empty:

        active_hosts = df[
            df['CPUUtilization'] > 0
        ].shape[0]

        total_power = df[
            'PowerConsumption(W)'
        ].sum()

        average_power = df[
            'PowerConsumption(W)'
        ].mean()

        maximum_power = df[
            'PowerConsumption(W)'
        ].max()

        total_energy = df[
            'TotalEnergyConsumption(Wh)'
        ].sum()

    else:

        total_power = total_row[
            'PowerConsumption(W)'
        ].values[0]

        total_energy = total_row[
            'TotalEnergyConsumption(Wh)'
        ].values[0]

        hosts_df = df[
            df['Host'] != 'Total'
        ]

        active_hosts = hosts_df[
            hosts_df['CPUUtilization'] > 0
        ].shape[0]

        average_power = hosts_df[
            'PowerConsumption(W)'
        ].mean()

        maximum_power = hosts_df[
            'PowerConsumption(W)'
        ].max()

    return {
        'total_power_consumption': total_power,
        'average_power_consumption': average_power,
        'max_power_consumption': maximum_power,
        'active_hosts': active_hosts,
        'total_energy_consumption': total_energy
    }


# ---------------------------------------------------------------------------
# TEMPO DE EXECUÇÃO DAS CLOUDLETS
# ---------------------------------------------------------------------------

def analyze_execution_times(df):
    """
    Analisa tempos de execução das Cloudlets de uma estratégia.
    """

    if df is None or df.empty:
        return None

    exec_time_column = None

    for column in df.columns:
        if (
            'Exec' in column
            and 'Time' in column
        ):
            exec_time_column = column
            break

    if exec_time_column is None:
        return None

    execution_times = pd.to_numeric(
        df[exec_time_column],
        errors='coerce'
    )

    return {
        'min_execution_time':
            execution_times.min(),

        'max_execution_time':
            execution_times.max(),

        'avg_execution_time':
            execution_times.mean(),

        'total_execution_time':
            execution_times.sum(),

        # O CSV contém apenas Cloudlets presentes na lista
        # de finalizadas do broker.
        'cloudlet_count':
            len(df)
    }


# ---------------------------------------------------------------------------
# DISTRIBUIÇÃO DAS VMs
# ---------------------------------------------------------------------------

def analyze_vm_distribution(df):
    """
    Calcula a distribuição de VMs/Cloudlets por Host.

    Neste experimento existe uma Cloudlet por VM.
    """

    if (
        df is None
        or df.empty
        or 'Host' not in df.columns
    ):
        return None

    vm_per_host = (
        df.groupby('Host')
        .size()
    )

    if vm_per_host.empty:
        return None

    return {
        'max_vms_per_host':
            vm_per_host.max(),

        'min_vms_per_host':
            vm_per_host.min()
    }


# ---------------------------------------------------------------------------
# VALIDAÇÃO DAS CLOUDLETS
# ---------------------------------------------------------------------------

def validate_cloudlets(cloudlet_counts, policy_df):
    """
    Valida a completude da execução de uma estratégia.

    São comparados três valores independentes:

    - Cloudlets submetidas ao Broker;
    - Cloudlets finalizadas pelo Broker;
    - linhas presentes no CSV de Cloudlets finalizadas.

    Uma execução é considerada completa quando os três valores coincidem.
    """

    if cloudlet_counts is None:
        return None

    submitted = int(
        cloudlet_counts['submitted']
    )

    finished = int(
        cloudlet_counts['finished']
    )

    policy_count = (
        len(policy_df)
        if policy_df is not None
        else None
    )

    broker_complete = (
        submitted == finished
    )

    policy_csv_consistent = (
        policy_count is not None
        and finished == policy_count
    )

    complete = (
        broker_complete
        and policy_csv_consistent
        and submitted == policy_count
    )

    missing = max(
        submitted - finished,
        0
    )

    return {
        'submitted': submitted,
        'finished': finished,
        'policy_count': policy_count,
        'missing': missing,
        'broker_complete': broker_complete,
        'policy_csv_consistent': policy_csv_consistent,
        'complete': complete
    }


# ---------------------------------------------------------------------------
# ANÁLISE CENTRALIZADA DAS ESTRATÉGIAS
# ---------------------------------------------------------------------------

def analyze_strategies(strategies):
    """
    Analisa automaticamente todas as estratégias descobertas.
    """

    results = {}

    for strategy_name, data in strategies.items():

        print(
            f'\nAnalyzing strategy: {strategy_name}'
        )

        strategy_results = {}

        # --------------------------------------------------------------
        # Energia
        # --------------------------------------------------------------
        power_df = data.get('power')

        if power_df is not None:

            power_results = (
                analyze_power_consumption(
                    power_df
                )
            )

            if power_results:
                strategy_results['power'] = (
                    power_results
                )

        else:
            print(
                f"Warning: strategy '{strategy_name}' "
                'has no power data.'
            )

        # --------------------------------------------------------------
        # Cloudlets / execução
        # --------------------------------------------------------------
        policy_df = data.get('policy')

        if policy_df is not None:

            execution_results = (
                analyze_execution_times(
                    policy_df
                )
            )

            if execution_results:
                strategy_results[
                    'execution'
                ] = execution_results

            distribution_results = (
                analyze_vm_distribution(
                    policy_df
                )
            )

            if distribution_results:
                strategy_results[
                    'distribution'
                ] = distribution_results

        else:
            print(
                f"Warning: strategy '{strategy_name}' "
                'has no policy data.'
            )

        # --------------------------------------------------------------
        # Validação das Cloudlets
        # --------------------------------------------------------------
        cloudlet_counts = data.get(
            'cloudlets'
        )

        validation_results = validate_cloudlets(
            cloudlet_counts,
            policy_df
        )

        if validation_results is not None:

            strategy_results[
                'cloudlet_validation'
            ] = validation_results

            if not validation_results['complete']:
                print(
                    f"Warning: incomplete or inconsistent Cloudlet result "
                    f"for strategy '{strategy_name}'. "
                    f"Submitted={validation_results['submitted']}, "
                    f"Finished={validation_results['finished']}, "
                    f"PolicyCSV={validation_results['policy_count']}."
                )

        else:
            print(
                f"Warning: strategy '{strategy_name}' "
                'has no Cloudlet submission/finalization counts.'
            )

        # --------------------------------------------------------------
        # Migrações
        # --------------------------------------------------------------
        migration_results = data.get(
            'migrations'
        )

        if migration_results is not None:

            strategy_results[
                'migrations'
            ] = migration_results

        else:
            print(
                f"Warning: strategy '{strategy_name}' "
                'has no migration count data.'
            )

        results[
            strategy_name
        ] = strategy_results

    return results


# ---------------------------------------------------------------------------
# GRÁFICOS
# ---------------------------------------------------------------------------

def plot_total_energy_comparison(results):

    strategies = []
    values = []

    for strategy, data in results.items():

        power = data.get('power')

        if power is None:
            continue

        strategies.append(strategy)

        values.append(
            power[
                'total_energy_consumption'
            ]
        )

    if not strategies:
        print(
            'No energy data available for plotting.'
        )
        return

    PLOTS_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    plt.figure(
        figsize=(10, 6)
    )

    plt.bar(
        strategies,
        values
    )

    plt.title(
        'Total Energy Consumption by Migration Strategy'
    )

    plt.xlabel(
        'Migration Strategy'
    )

    plt.ylabel(
        'Total Energy Consumption (Wh)'
    )

    plt.tight_layout()

    plt.savefig(
        PLOTS_DIR
        / 'total_energy_comparison.png'
    )

    plt.close()


def plot_max_execution_time_comparison(results):

    strategies = []
    values = []

    for strategy, data in results.items():

        execution = data.get(
            'execution'
        )

        if execution is None:
            continue

        strategies.append(
            strategy
        )

        values.append(
            execution[
                'max_execution_time'
            ]
        )

    if not strategies:
        return

    PLOTS_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    plt.figure(
        figsize=(10, 6)
    )

    plt.bar(
        strategies,
        values
    )

    plt.title(
        'Maximum Execution Time by Migration Strategy'
    )

    plt.xlabel(
        'Migration Strategy'
    )

    plt.ylabel(
        'Maximum Execution Time (seconds)'
    )

    plt.tight_layout()

    plt.savefig(
        PLOTS_DIR
        / 'max_execution_time_comparison.png'
    )

    plt.close()


def plot_total_execution_time_comparison(
        results):

    strategies = []
    values = []

    for strategy, data in results.items():

        execution = data.get(
            'execution'
        )

        if execution is None:
            continue

        strategies.append(
            strategy
        )

        values.append(
            execution[
                'total_execution_time'
            ]
        )

    if not strategies:
        return

    PLOTS_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    plt.figure(
        figsize=(10, 6)
    )

    plt.bar(
        strategies,
        values
    )

    plt.title(
        'Total Execution Time by Migration Strategy'
    )

    plt.xlabel(
        'Migration Strategy'
    )

    plt.ylabel(
        'Total Execution Time (seconds)'
    )

    plt.tight_layout()

    plt.savefig(
        PLOTS_DIR
        / 'total_execution_time_comparison.png'
    )

    plt.close()


def plot_migration_counts_comparison(results):
    """
    Gera um gráfico geral do ciclo de vida das migrações por estratégia.

    Exibe, quando disponíveis:
    - planejadas;
    - iniciadas;
    - concluídas;
    - bem-sucedidas;
    - falhas;
    - planejadas que não iniciaram;
    - iniciadas que não terminaram.

    Métricas inexistentes em CSVs históricos são omitidas do gráfico.
    """

    rows = []

    for strategy, data in results.items():
        migrations = data.get('migrations')

        if migrations is None:
            continue

        rows.append({
            'Strategy': strategy,
            'Planned': migrations.get('planned'),
            'Started': migrations.get('started'),
            'Completed': migrations.get('completed'),
            'Successful': migrations.get('successful'),
            'Failed': migrations.get('failed'),
            'Not Started': migrations.get('not_started'),
            'Unfinished': migrations.get('unfinished')
        })

    if not rows:
        print(
            'No migration count data available for plotting.'
        )
        return

    plot_df = pd.DataFrame(rows).set_index('Strategy')

    # Remove métricas que não possuem nenhum valor conhecido.
    plot_df = plot_df.dropna(
        axis=1,
        how='all'
    )

    if plot_df.empty:
        return

    PLOTS_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    ax = plot_df.plot(
        kind='bar',
        figsize=(14, 7)
    )

    ax.set_title(
        'VM Migration Lifecycle by Strategy'
    )

    ax.set_xlabel(
        'Migration Strategy'
    )

    ax.set_ylabel(
        'Number of VM Migrations'
    )

    ax.tick_params(
        axis='x',
        rotation=0
    )

    ax.legend(
        title='Migration State',
        ncol=2
    )

    plt.tight_layout()

    plt.savefig(
        PLOTS_DIR
        / 'migration_counts_comparison.png'
    )

    plt.close()


def plot_vm_distribution(strategies):

    PLOTS_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    for strategy_name, data in strategies.items():

        df = data.get('policy')

        if (
            df is None
            or df.empty
            or 'Host' not in df.columns
        ):
            continue

        vm_counts = (
            df.groupby('Host')
            .size()
            .sort_values(
                ascending=False
            )
        )

        plt.figure(
            figsize=(12, 6)
        )

        vm_counts.plot(
            kind='bar'
        )

        plt.title(
            f'VM Distribution Across Hosts '
            f'({strategy_name})'
        )

        plt.xlabel(
            'Host ID'
        )

        plt.ylabel(
            'Number of VMs'
        )

        plt.tight_layout()

        plt.savefig(
            PLOTS_DIR
            / f'vm_distribution_{strategy_name}.png'
        )

        plt.close()


# ---------------------------------------------------------------------------
# TABELA COMPARATIVA FINAL
# ---------------------------------------------------------------------------

def generate_comparison_table(results):
    """
    Gera uma linha para cada estratégia descoberta.

    Uma estratégia não precisa possuir obrigatoriamente todos os tipos
    de resultado. Dados ausentes permanecem vazios no CSV.
    """

    comparison_data = []

    for strategy_name, data in results.items():

        row = {
            'Strategy': strategy_name
        }

        # --------------------------------------------------------------
        # Energia
        # --------------------------------------------------------------
        power = data.get(
            'power'
        )

        if power is not None:

            row.update({
                'Total Power (W)':
                    power[
                        'total_power_consumption'
                    ],

                'Average Power (W)':
                    power[
                        'average_power_consumption'
                    ],

                'Total Energy (Wh)':
                    power[
                        'total_energy_consumption'
                    ],

                'Active Hosts':
                    power[
                        'active_hosts'
                    ]
            })

        # --------------------------------------------------------------
        # Execução
        # --------------------------------------------------------------
        execution = data.get(
            'execution'
        )

        if execution is not None:

            row.update({
                'Total Exec Time (s)':
                    execution[
                        'total_execution_time'
                    ],

                'Avg Exec Time (s)':
                    execution[
                        'avg_execution_time'
                    ],

                'Max Exec Time (s)':
                    execution[
                        'max_execution_time'
                    ],

                'Min Exec Time (s)':
                    execution[
                        'min_execution_time'
                    ],

                'Cloudlet Count':
                    execution[
                        'cloudlet_count'
                    ]
            })

        # --------------------------------------------------------------
        # Validação das Cloudlets
        # --------------------------------------------------------------
        validation = data.get(
            'cloudlet_validation'
        )

        if validation is not None:

            row.update({
                'Submitted Cloudlets':
                    validation['submitted'],

                'Finished Cloudlets':
                    validation['finished'],

                'Missing Cloudlets':
                    validation['missing'],

                'Simulation Complete':
                    validation['complete']
            })

        # --------------------------------------------------------------
        # Migrações
        # --------------------------------------------------------------
        migrations = data.get(
            'migrations'
        )

        if migrations is not None:

            row.update({
                'Planned Migrations':
                    migrations.get(
                        'planned'
                    ),

                'Started Migrations':
                    migrations[
                        'started'
                    ],

                'Completed Migrations':
                    migrations[
                        'completed'
                    ],

                'Successful Migrations':
                    migrations[
                        'successful'
                    ],

                'Failed Migrations':
                    migrations[
                        'failed'
                    ],

                'Not Started Migrations':
                    migrations.get(
                        'not_started'
                    ),

                'Unfinished Migrations':
                    migrations[
                        'unfinished'
                    ]
            })

        comparison_data.append(
            row
        )

    comparison_df = pd.DataFrame(
        comparison_data
    )

    PLOTS_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    output_file = (
        PLOTS_DIR
        / 'complete_strategy_comparison.csv'
    )

    comparison_df.to_csv(
        output_file,
        index=False
    )

    print(
        '\nComplete Strategy Comparison:'
    )

    print(
        comparison_df.to_string(
            index=False
        )
    )

    print(
        f'\nComparison CSV saved to: '
        f'{output_file}'
    )


# ---------------------------------------------------------------------------
# IMPRESSÃO DOS RESULTADOS
# ---------------------------------------------------------------------------

def print_analysis_summary(results):

    print(
        '\n======================================'
    )

    print(
        ' MIGRATION STRATEGY ANALYSIS'
    )

    print(
        '======================================'
    )

    for strategy, data in results.items():

        print(
            f'\nStrategy: {strategy}'
        )

        print(
            '--------------------------------------'
        )

        power = data.get(
            'power'
        )

        if power:

            print(
                'Power:'
            )

            print(
                '  Total Power: '
                f"{power['total_power_consumption']:.2f} W"
            )

            print(
                '  Average Power: '
                f"{power['average_power_consumption']:.2f} W"
            )

            print(
                '  Total Energy: '
                f"{power['total_energy_consumption']:.2f} Wh"
            )

            print(
                '  Active Hosts: '
                f"{power['active_hosts']}"
            )

        execution = data.get(
            'execution'
        )

        if execution:

            print(
                'Execution:'
            )

            print(
                '  Cloudlet Count: '
                f"{execution['cloudlet_count']}"
            )

            print(
                '  Total Exec Time: '
                f"{execution['total_execution_time']:.4f} s"
            )

            print(
                '  Average Exec Time: '
                f"{execution['avg_execution_time']:.4f} s"
            )

            print(
                '  Maximum Exec Time: '
                f"{execution['max_execution_time']:.4f} s"
            )

            print(
                '  Minimum Exec Time: '
                f"{execution['min_execution_time']:.4f} s"
            )

        validation = data.get(
            'cloudlet_validation'
        )

        if validation:

            print(
                'Cloudlet validation:'
            )

            print(
                '  Submitted: '
                f"{validation['submitted']}"
            )

            print(
                '  Finished: '
                f"{validation['finished']}"
            )

            print(
                '  Policy CSV rows: '
                f"{validation['policy_count']}"
            )

            print(
                '  Missing: '
                f"{validation['missing']}"
            )

            print(
                '  Complete: '
                f"{validation['complete']}"
            )

        migrations = data.get(
            'migrations'
        )

        if migrations:

            print(
                'Migrations:'
            )

            if migrations.get('planned') is not None:
                print(
                    '  Planned: '
                    f"{migrations['planned']}"
                )

            print(
                '  Started: '
                f"{migrations['started']}"
            )

            print(
                '  Completed: '
                f"{migrations['completed']}"
            )

            print(
                '  Successful: '
                f"{migrations['successful']}"
            )

            print(
                '  Failed: '
                f"{migrations['failed']}"
            )

            if migrations.get('not_started') is not None:
                print(
                    '  Not Started: '
                    f"{migrations['not_started']}"
                )

            print(
                '  Unfinished: '
                f"{migrations['unfinished']}"
            )


# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------

def main():

    print(
        'Starting migration simulation analysis...'
    )

    # ------------------------------------------------------------------
    # 1. Descobre automaticamente as estratégias.
    # ------------------------------------------------------------------

    strategies = discover_strategies()

    if not strategies:

        print(
            'No strategies available for analysis.'
        )

        return

    print(
        '\nStrategies discovered:'
    )

    for strategy in strategies:

        available_data = ', '.join(
            strategies[strategy].keys()
        )

        print(
            f'  {strategy}: {available_data}'
        )

    # ------------------------------------------------------------------
    # 2. Analisa todas as estratégias.
    # ------------------------------------------------------------------

    results = analyze_strategies(
        strategies
    )

    # ------------------------------------------------------------------
    # 3. Mostra resumo.
    # ------------------------------------------------------------------

    print_analysis_summary(
        results
    )

    # ------------------------------------------------------------------
    # 4. Gera gráficos.
    # ------------------------------------------------------------------

    print(
        '\nGenerating plots...'
    )

    plot_total_energy_comparison(
        results
    )

    plot_max_execution_time_comparison(
        results
    )

    plot_total_execution_time_comparison(
        results
    )

    plot_migration_counts_comparison(
        results
    )

    plot_vm_distribution(
        strategies
    )

    # ------------------------------------------------------------------
    # 5. Gera CSV comparativo final.
    # ------------------------------------------------------------------

    print(
        '\nGenerating comprehensive comparison table...'
    )

    generate_comparison_table(
        results
    )

    print(
        '\nAnalysis completed.'
    )


if __name__ == '__main__':
    main()