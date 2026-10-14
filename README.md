# migrations-simulations
Repositório para simulação de algoritmos de migração de VMs (Best Fit e First Fit) utilizando o framework CloudSim Plus, contendo scripts automatizados de execução e ferramentas de análise de desempenho e eficiência energética.

# Sobre o Projeto
Este projeto realiza simulações avançadas de migração de Máquinas Virtuais (VMs) com o CloudSim Plus (versão 8.5.5). O objetivo principal é analisar o comportamento e o impacto de diferentes políticas de alocação de recursos:   
- Best Fit
- First Fit

# 🛠️ Tecnologias e Requisitos
- Java 17+ (configurado via Maven compiler plugin)
- Maven para gerenciamento de dependências e build
- CloudSim Plus (org.cloudsimplus:cloudsimplus:8.5.5)
- Python para os scripts de análise de dados e geração de gráficos

# 🚀 Como Executar as Simulações
O repositório inclui scripts automatizados para compilar o projeto Maven, copiar dependências e executar as políticas de migração em processos separados, garantindo a formatação correta dos resultados:
- Para Linux / macOS:
´´´ sh ./execute_migrations.sh 
´´´[cite: 1]

- Para Windows:
´´´ sh ./execute_migrations_windows.sh
```[cite: 2]
