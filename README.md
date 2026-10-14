# VM Migration Simulations with [CloudSim Plus](https://cloudsimplus.org/)

This repository contains code for simulating VM migration algorithms, running experiments, and analyzing results.

---

## Table of Contents

1. [VM Migration Algorithms](#vm-migration-algorithms)
2. [Running the Experiments](#running-the-experiments)
   - [Requirements](#requirements)
   - [Setup Instructions](#setup-instructions)
   - [Running Simulations](#running-simulations)
3. [Simulations Results](#simulations-results)
4. [Analysis](#analysis)
   - [Prerequisites](#prerequisites)
   - [Setup](#setup)
   - [Running the Analysis](#running-the-analysis)
5. [Analysis Results](#analysis-results)
   - [Generated Plots and Files](#generated-plots-and-files)
6. [Energy Calculation Method](#energy-calculation-method)
7. [Understanding the Results](#understanding-the-results)

---

## VM Migration Algorithms

This project simulates the following VM migration algorithms:

- **Best Fit**
- **First Fit**

---

## Running the Experiments

### Requirements

Ensure the following tools are installed:

- [JDK 17+](https://www.oracle.com/java/technologies/downloads/)
- [Maven 3.9.9](https://maven.apache.org/download.cgi)
- [Git](https://git-scm.com/)

### Setup Instructions

1. Install the required tools: [JDK 17+](https://www.oracle.com/java/technologies/downloads/), [Maven 3.9.9](https://maven.apache.org/download.cgi), and [Git](https://git-scm.com/).
2. Clone this repository:

   ```bash
   git clone https://github.com/GuilhermeFernandes01/migrations-simulations-algorithms-cloudsimplus
   ```

3. Navigate to the repository directory:

   ```bash
   cd migrations-simulations-algorithms-cloudsimplus
   ```

### Running Simulations

Run the VM migration simulations using:

```bash
sh ./execute_migrations.sh
```

---

## Simulations Results

Simulation results are saved in the `migrations_results` folder. Each migration algorithm generates a separate CSV file:

- `migration_best_fit_policy.csv`
- `migration_first_fit_policy.csv`

These files contain detailed data for each migration run.

---

## Analysis

The analysis scripts and related files are located in the `analysis` folder.

### Prerequisites

- [Python 3.6+](https://www.python.org/downloads/)
- Result files in CSV format (`migration_*_power.csv`) located in the `migrations_results` folder.

### Setup

Run the setup script to create a virtual environment and install dependencies:

```bash
chmod +x setup.sh
./setup.sh
```

### Running the Analysis

After setup, execute the analysis using one of the following methods:

1. Activate the virtual environment and run the analysis script:

   ```bash
   source venv/bin/activate
   python3 analyze_migration.py
   ```

2. Or use the provided execution script:

   ```bash
   ./run_analysis.sh
   ```

---

## Analysis Results

The analysis produces the following outputs:

1. **Statistical Summary**: Displayed in the console.
2. **Visualization Graph**: Saved in the `plots` directory, comparing total energy consumption for each strategy.
3. **Comparative CSV File**: A table summarizing all strategies.

### Generated Plots and Files

The analysis script generates the following items in the `plots` directory:

1. **total_energy_comparison.png**
2. **max_execution_time_comparison.png**
3. **total_execution_time_comparison.png**
4. **complete_strategy_comparison.csv**

These visualizations and data files help evaluate VM migration strategies from multiple perspectives: energy efficiency, performance consistency, and overall execution time.

---

## Energy Calculation Method

Energy consumption is calculated as follows:

1. **Host Power Calculation**: Power (W) is calculated based on CPU utilization using the `PowerModelHostSimple` model.
2. **Energy Calculation**: Total energy (Wh) is computed by multiplying power by simulation time (converted to hours).
3. **Datacenter Energy Consumption**: The total energy consumption is the sum of all hosts' energy consumption.

---

## Understanding the Results

- **Energy Consumption**: Lower values indicate more energy-efficient strategies.
- **Maximum Execution Time**: Lower values indicate more consistent performance and better QoS guarantees.
- **Total Execution Time**: Lower values indicate better overall workload processing efficiency.
- **Active Hosts**: Fewer active hosts typically correlate with better energy efficiency, but may impact performance.
