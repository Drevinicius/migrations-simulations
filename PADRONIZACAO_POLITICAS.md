# Padronização para Implementação de Políticas de Migração

Este documento define o padrão mínimo que deve ser seguido ao adicionar uma nova política de migração de VMs ao projeto.

O objetivo é garantir que novas políticas possam ser **executadas, identificadas e analisadas automaticamente**, sem exigir alterações no `execute_migrations.sh` ou no `analysis/analyze_migration.py` a cada implementação.

A padronização cobre:

- nome das classes Java;
- identificador interno da estratégia;
- listeners e rastreamento de migrações;
- arquivos CSV obrigatórios;
- validação das Cloudlets;
- integração com os scripts de execução;
- descoberta automática pelo Python;
- responsabilidades que continuam específicas de cada política.

---

## 1. Convenção de nome da classe

Toda política executável deve estar no pacote:

```java
package simulations;
```

E a classe deve seguir o padrão:

```text
Migration<NomeDaPolitica>Policy
```

Exemplos:

```text
MigrationBestFitPolicy
MigrationFirstFitPolicy
MigrationWorstFitPolicy
MigrationRandomFitPolicy
```

### Por que esse padrão existe?

O `execute_migrations.sh` não possui mais uma lista fixa de políticas. Depois da compilação, ele procura automaticamente todas as classes que correspondem a:

```bash
for CLASS_FILE in target/classes/simulations/Migration*Policy.class; do
```

Portanto, uma classe que não respeitar `Migration*Policy` não será executada automaticamente.

A política também deve possuir um `main` executável:

```java
public static void main(String[] args) {
    new MigrationWorstFitPolicy();
}
```

---

## 2. Identificador da estratégia

Cada política deve possuir um identificador textual único, escrito em `snake_case`.

Exemplos:

```text
best_fit
first_fit
worst_fit
random_fit
```

Esse identificador deve ser **exatamente o mesmo em todos os resultados da estratégia**.

Para uma nova política `Worst Fit`, por exemplo:

```text
Identificador: worst_fit
```

Os arquivos devem ser:

```text
migration_worst_fit_policy.csv
migration_worst_fit_power.csv
```

E os writers devem registrar:

```java
MigrationCsvWriter.writeOrUpdate("worst_fit", migrationTracker);
SimulationCsvWriter.writeOrUpdate("worst_fit", broker);
```

### Recomendação

Para reduzir o risco de escrever identificadores diferentes em pontos distintos do código, uma política nova pode declarar uma constante:

```java
private static final String STRATEGY_ID = "worst_fit";
```

E reutilizá-la:

```java
MigrationCsvWriter.writeOrUpdate(STRATEGY_ID, migrationTracker);
SimulationCsvWriter.writeOrUpdate(STRATEGY_ID, broker);
```

### Por que esse identificador precisa ser igual?

O Python agrupa os arquivos e métricas pelo nome da estratégia. Por exemplo:

```text
migration_worst_fit_policy.csv -> worst_fit
migration_worst_fit_power.csv  -> worst_fit
migration_counts.csv           -> Strategy = worst_fit
simulation_counts.csv          -> Strategy = worst_fit
```

Se um desses nomes for diferente, os dados podem ser interpretados como estratégias distintas.

---

## 3. `MigrationTracker` obrigatório

Cada política deve possuir sua própria instância de `MigrationTracker`:

```java
private final MigrationTracker migrationTracker = new MigrationTracker();
```

O tracker centraliza a contagem das migrações e classifica os eventos em:

```text
Started
Completed
Successful
Failed
Unfinished
```

As relações esperadas são:

```text
Started = Completed + Unfinished
Completed = Successful + Failed
```

O próprio `MigrationTracker` verifica essa consistência.

### Por que usar o tracker?

A contagem antiga considerava apenas um incremento simples quando uma migração começava. Isso não permitia saber se a migração realmente terminou, falhou ou permaneceu pendente.

O tracker separa esses estados e permite que todas as políticas produzam métricas comparáveis.

---

## 4. Registrar o resultado da migração no Datacenter

Logo após a criação do Datacenter, a política deve registrar o listener que informa o resultado das migrações:

```java
final Datacenter datacenter0 = createDatacenter();

datacenter0.addOnVmMigrationFinishListener(
    migrationTracker::recordResult
);
```

Esse listener utiliza o evento do Datacenter porque ele informa se a migração foi concluída com sucesso ou falhou.

### Por que isso é necessário?

O evento de início da VM indica apenas que a migração começou. Ele não é suficiente para afirmar que ela terminou corretamente.

O `recordResult()` permite separar:

```text
SuccessfulMigrations
FailedMigrations
```

---

## 5. Registrar início e fim nas VMs

Todas as VMs submetidas devem registrar os listeners de migração.

O padrão atual é:

```java
list.forEach(
    vm -> vm.addOnMigrationStartListener(this::startMigration)
);

list.forEach(
    vm -> vm.addOnMigrationFinishListener(this::finishMigration)
);

list.forEach(
    vm -> vm.enableUtilizationStats()
);
```

O método de início deve registrar a migração no tracker antes de chamar a lógica compartilhada:

```java
private void startMigration(final VmHostEventInfo info) {
    migrationTracker.recordStart(info);

    Shared.startMigration(
        migrationTracker.getStarted(),
        simulation,
        info
    );
}
```

O método de finalização mantém a atualização de estado dos hosts:

```java
private void finishMigration(final VmHostEventInfo info) {
    Shared.finishMigration(hostList, info);
}
```

### Por que existem listeners diferentes?

Cada listener possui uma responsabilidade distinta:

```text
VM Migration Start
    -> registra que uma migração realmente começou

VM Migration Finish
    -> atualiza o estado utilizado pela simulação/diagnóstico

Datacenter Migration Finish
    -> informa se a migração terminou com sucesso ou falha
```

---

## 6. Execução da simulação

A política deve criar normalmente:

```text
CloudSimPlus
Datacenter
Broker
VMs
Cloudlets
```

E iniciar a simulação por meio de:

```java
simulation.start();
```

Após o término, deve utilizar a lista de Cloudlets finalizadas do broker:

```java
final List<Cloudlet> finishedList =
    broker.getCloudletFinishedList();
```

Essa lista é a fonte do CSV de execução da política.

### Importante

O CSV de `policy` contém apenas Cloudlets presentes na lista de finalizadas.

Por isso, se 150 Cloudlets forem submetidas e apenas 149 terminarem, o arquivo terá 149 registros. O Python usa essa informação na validação de completude.

---

## 7. CSV obrigatório de Cloudlets da política

Cada estratégia deve gerar:

```text
migrations_results/migration_<strategy>_policy.csv
```

Exemplo:

```text
migrations_results/migration_worst_fit_policy.csv
```

O projeto utiliza o `CloudletsTableBuilder` do próprio CloudSim Plus:

```java
CsvTable csv = new CsvTable();

csv.setPrintStream(
    new PrintStream(
        new java.io.File(
            "migrations_results/migration_worst_fit_policy.csv"
        )
    )
);

new CloudletsTableBuilder(
    finishedList,
    csv
).build();
```

### Por que manter esse formato?

O `analyze_migration.py` identifica automaticamente qualquer arquivo no padrão:

```text
migration_<strategy>_policy.csv
```

O Python também usa a quantidade de linhas desse arquivo como o número de Cloudlets finalizadas disponíveis para análise.

---

## 8. CSV obrigatório de energia

Cada estratégia também deve gerar:

```text
migrations_results/migration_<strategy>_power.csv
```

Exemplo:

```text
migrations_results/migration_worst_fit_power.csv
```

O padrão atual utiliza:

```java
CsvTable powerCsv = new CsvTable();

powerCsv.setPrintStream(
    new PrintStream(
        new java.io.File(
            "migrations_results/migration_worst_fit_power.csv"
        )
    )
);

Shared.exportPowerConsumptionToCsv(
    hostList,
    simulation,
    powerCsv,
    "Worst Fit"
);
```

### Por que esse arquivo é obrigatório?

O Python usa esse CSV para calcular e comparar métricas como:

```text
Total Power
Average Power
Total Energy
Active Hosts
```

Sem o arquivo `_power.csv`, a estratégia ainda pode ser descoberta, mas a análise exibirá um aviso de que os dados de energia estão ausentes.

---

## 9. Exportar as métricas de migração

Ao final da execução, a política deve imprimir o resumo:

```java
migrationTracker.printSummary();
```

E registrar os dados no CSV compartilhado:

```java
MigrationCsvWriter.writeOrUpdate(
    "worst_fit",
    migrationTracker
);
```

O arquivo gerado é:

```text
migrations_results/migration_counts.csv
```

Com o formato:

```csv
Strategy,StartedMigrations,CompletedMigrations,SuccessfulMigrations,FailedMigrations,UnfinishedMigrations
```

Exemplo:

```csv
worst_fit,8,8,8,0,0
```

### Significado das colunas

| Coluna | Significado |
|---|---|
| `StartedMigrations` | Migrações que chegaram a iniciar |
| `CompletedMigrations` | Migrações que receberam um resultado final |
| `SuccessfulMigrations` | Migrações concluídas com sucesso |
| `FailedMigrations` | Migrações concluídas com resultado de falha |
| `UnfinishedMigrations` | Migrações iniciadas que não receberam resultado final |

### Por que o writer atualiza em vez de apenas adicionar?

Cada estratégia deve ocupar uma única linha. Se a mesma política for executada novamente, sua linha é substituída em vez de duplicada.

---

## 10. Exportar a validação das Cloudlets

Cada política deve registrar o estado final do Broker:

```java
SimulationCsvWriter.writeOrUpdate(
    "worst_fit",
    broker
);
```

O arquivo compartilhado é:

```text
migrations_results/simulation_counts.csv
```

Com o formato:

```csv
Strategy,SubmittedCloudlets,FinishedCloudlets
```

Exemplo:

```csv
worst_fit,150,150
```

O `SimulationCsvWriter` obtém os valores diretamente do Broker:

```java
broker.getCloudletSubmittedList().size()
broker.getCloudletFinishedList().size()
```

### Por que esse arquivo existe?

O CSV `*_policy.csv` contém somente as Cloudlets finalizadas. Apenas contar suas linhas não informa quantas Cloudlets deveriam ter terminado.

Por isso o Python cruza três valores independentes:

```text
SubmittedCloudlets
FinishedCloudlets
linhas do migration_<strategy>_policy.csv
```

A execução só é considerada completa quando os três valores coincidem.

A lógica utilizada é equivalente a:

```python
complete = (
    submitted == finished
    and finished == policy_count
    and submitted == policy_count
)
```

Isso permite detectar automaticamente casos como:

```text
Submitted = 150
Finished = 149
Policy CSV = 149
Simulation Complete = False
```

---

## 11. Estrutura mínima esperada de uma política

Uma política nova deve seguir, conceitualmente, esta estrutura:

```java
package simulations;

public final class MigrationWorstFitPolicy {

    private static final String STRATEGY_ID = "worst_fit";

    private final MigrationTracker migrationTracker =
        new MigrationTracker();

    public static void main(String[] args) {
        new MigrationWorstFitPolicy();
    }

    private MigrationWorstFitPolicy() {

        simulation = new CloudSimPlus();

        final Datacenter datacenter0 = createDatacenter();

        datacenter0.addOnVmMigrationFinishListener(
            migrationTracker::recordResult
        );

        broker = new DatacenterBrokerSimple(simulation);

        createAndSubmitVms(broker);
        createAndSubmitCloudlets(broker);

        simulation.start();

        final List<Cloudlet> finishedList =
            broker.getCloudletFinishedList();

        // 1. migration_<strategy>_policy.csv
        //    usando CloudletsTableBuilder

        // 2. migration_<strategy>_power.csv
        //    usando Shared.exportPowerConsumptionToCsv

        migrationTracker.printSummary();

        MigrationCsvWriter.writeOrUpdate(
            STRATEGY_ID,
            migrationTracker
        );

        SimulationCsvWriter.writeOrUpdate(
            STRATEGY_ID,
            broker
        );
    }

    private void startMigration(final VmHostEventInfo info) {
        migrationTracker.recordStart(info);

        Shared.startMigration(
            migrationTracker.getStarted(),
            simulation,
            info
        );
    }

    private void finishMigration(final VmHostEventInfo info) {
        Shared.finishMigration(hostList, info);
    }
}
```

Esse trecho é apenas um esqueleto da infraestrutura comum. A lógica de seleção e alocação de VMs continua sendo responsabilidade da política.

---

## 12. O que continua específico de cada política

A padronização não define como uma política deve escolher uma VM ou um Host de destino.

Cada implementação continua responsável por definir sua própria política de alocação, por exemplo dentro de `createDatacenter()`:

```java
this.allocationPolicy = new MinhaNovaPoliticaDeMigracao(...);
```

Também são específicos da política:

- critério de escolha do Host de destino;
- critério de seleção da VM;
- limiares adicionais, caso existam;
- estruturas auxiliares necessárias ao algoritmo;
- comportamento interno da política.

A infraestrutura de métricas não deve alterar a lógica de decisão da estratégia.

---

## 13. Scheduler dos Hosts

A criação do Host deve seguir o protocolo experimental escolhido para a estratégia.

Atualmente existem duas formas compartilhadas no projeto:

```java
Shared.createHost(...)
```

E:

```java
Shared.createHostWithFixedScheduler(...)
```

O `FixedVmSchedulerTimeShared` foi criado para corrigir a contabilização da própria VM candidata durante determinadas migrações.

### Regra importante

A escolha do scheduler **não deve ser alterada silenciosamente apenas para atender à padronização dos arquivos**. Ela faz parte do comportamento experimental e pode modificar os resultados da simulação.

Se uma nova política usar o scheduler corrigido ou outro scheduler específico, isso deve ser documentado na própria implementação ou no experimento comparativo.

---

## 14. Descoberta automática pelo `execute_migrations.sh`

O script de execução é genérico e não deve precisar ser alterado para cada nova política.

Ele procura:

```bash
target/classes/simulations/Migration*Policy.class
```

E executa cada classe encontrada em um processo separado:

```bash
java \
  -Duser.language=en \
  -Duser.country=US \
  -cp "$CLASSPATH" \
  "simulations.$CLASS_NAME"
```

Portanto, para que uma política seja executada automaticamente, basta:

1. estar em `src/main/java/simulations`;
2. seguir o nome `Migration<Nome>Policy`;
3. possuir um `main` executável.

O script também remove resultados antigos antes da nova execução, evitando que arquivos de políticas removidas continuem sendo analisados.

---

## 15. Descoberta automática pelo Python

O `analyze_migration.py` procura automaticamente arquivos em:

```text
migrations_results/
```

Ele reconhece os padrões:

```text
migration_<strategy>_policy.csv
migration_<strategy>_power.csv
```

Para:

```text
migration_worst_fit_policy.csv
```

extrai:

```text
strategy = worst_fit
```

E para:

```text
migration_worst_fit_power.csv
```

extrai o mesmo identificador.

Depois associa também as linhas de:

```text
migration_counts.csv
simulation_counts.csv
```

que possuírem `Strategy = worst_fit`.

### Consequência prática

Ao adicionar uma política corretamente padronizada, **não é necessário editar o Python**.

O Python passa a incluí-la automaticamente em:

- análise de energia;
- análise de tempo de execução;
- validação de Cloudlets;
- métricas de migração;
- gráficos comparativos;
- `complete_strategy_comparison.csv`.

---

## 16. Resultados gerados pela análise

Depois das simulações, execute:

```bash
./analysis/run_analysis.sh
```

O script:

1. localiza o ambiente virtual Python;
2. executa `setup.sh` automaticamente caso necessário;
3. verifica as dependências;
4. executa `analyze_migration.py`.

O resultado consolidado é:

```text
analysis/plots/complete_strategy_comparison.csv
```

Ele pode conter, para cada estratégia:

```text
Total Power
Average Power
Total Energy
Active Hosts
Total Exec Time
Avg Exec Time
Max Exec Time
Min Exec Time
Cloudlet Count
Submitted Cloudlets
Finished Cloudlets
Missing Cloudlets
Simulation Complete
Started Migrations
Completed Migrations
Successful Migrations
Failed Migrations
Unfinished Migrations
```

---

## 17. Arquivos gerados não devem ser versionados

Diretórios gerados durante compilação, simulação e análise não devem fazer parte do código-fonte versionado.

Exemplos:

```text
target/
migrations_results/
analysis/plots/
analysis/venv/
```

Esses diretórios devem permanecer no `.gitignore`.

### Por que?

Eles são reconstruídos automaticamente pelos scripts e podem conter:

- binários compilados;
- resultados de execuções antigas;
- gráficos;
- ambientes virtuais específicos de uma máquina.

Versioná-los aumenta o repositório e pode fazer uma análise usar dados desatualizados.

---

## 18. Checklist para adicionar uma nova política

Antes de considerar uma política pronta, verificar:

- [ ] A classe está em `package simulations`.
- [ ] O nome segue `Migration<Nome>Policy`.
- [ ] A classe possui `public static void main`.
- [ ] Existe um identificador único em `snake_case`.
- [ ] O mesmo identificador é usado em todos os CSVs e writers.
- [ ] Existe um `MigrationTracker` próprio da estratégia.
- [ ] O Datacenter registra `migrationTracker::recordResult`.
- [ ] As VMs registram o listener de início da migração.
- [ ] As VMs registram o listener de finalização da migração.
- [ ] As estatísticas de utilização das VMs estão habilitadas quando exigidas pela análise atual.
- [ ] É gerado `migration_<strategy>_policy.csv`.
- [ ] É gerado `migration_<strategy>_power.csv`.
- [ ] `MigrationCsvWriter.writeOrUpdate(...)` é chamado.
- [ ] `SimulationCsvWriter.writeOrUpdate(...)` é chamado.
- [ ] A política aparece automaticamente no `execute_migrations.sh`.
- [ ] A política aparece automaticamente no `analyze_migration.py`.
- [ ] `Simulation Complete` é `True` ao final de uma execução válida.
- [ ] Os contadores de migração respeitam:

```text
Started = Completed + Unfinished
Completed = Successful + Failed
```

---

## 19. Exemplo completo de identificação

Para uma nova política chamada `Least Loaded`:

### Classe Java

```text
MigrationLeastLoadedPolicy.java
```

### Identificador

```text
least_loaded
```

### Arquivos gerados

```text
migration_least_loaded_policy.csv
migration_least_loaded_power.csv
```

### Registro das migrações

```java
MigrationCsvWriter.writeOrUpdate(
    "least_loaded",
    migrationTracker
);
```

### Registro das Cloudlets

```java
SimulationCsvWriter.writeOrUpdate(
    "least_loaded",
    broker
);
```

Com isso, o fluxo esperado é:

```text
MigrationLeastLoadedPolicy
        ↓
execute_migrations.sh encontra a classe
        ↓
Java executa a estratégia
        ↓
migration_least_loaded_policy.csv
migration_least_loaded_power.csv
migration_counts.csv
simulation_counts.csv
        ↓
analyze_migration.py identifica least_loaded
        ↓
gráficos + complete_strategy_comparison.csv
```

---

## 20. Princípio geral da padronização

A regra principal do projeto é:

> **A política deve se preocupar com a lógica de migração; a infraestrutura comum deve cuidar da execução, coleta, validação e análise dos resultados.**

Enquanto cada política respeitar os nomes, listeners e formatos definidos neste documento, novas estratégias podem ser adicionadas sem alterar os scripts de execução ou o código de análise.
