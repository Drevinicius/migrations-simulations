package simulations;

import ch.qos.logback.classic.Level;
import simulations.Shared.Config;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.CsvTable;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.listeners.DatacenterBrokerEventInfo;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumUtilization;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingLong;

/**
 * Simulação completa usando a política Random Fit com limiares estáticos.
 *
 * FLUXO PRINCIPAL:
 * 1) cria CloudSimPlus;
 * 2) cria Datacenter + 100 Hosts;
 * 3) cria Broker;
 * 4) cria/submete 150 VMs;
 * 5) cria/submete 150 Cloudlets, 1 por VM;
 * 6) inicia a simulação;
 * 7) a política detecta Hosts >80% ou <10% e propõe migrações;
 * 8) ao terminar, gera CSV de Cloudlets, energia, migrações
 *    e contagem de Cloudlets.
 *
 * RANDOM FIT:
 * Entre os Hosts candidatos válidos (que comportam a VM), escolhe
 * aleatoriamente um destino. Isso traz imprevisibilidade ao processo,
 * podendo gerar resultados diferentes a cada execução.
 */
public final class MigrationRandomFitPolicy {

    // Lista local usada para criar Cloudlets e registrar listeners nas VMs.
    private final List<Vm> vmList = new ArrayList<>();

    private final DatacenterBrokerSimple broker;

    // Rastreia migrações iniciadas, concluídas, bem-sucedidas,
    // falhas e não finalizadas.
    private final MigrationTracker migrationTracker =
        new MigrationTracker();

    private final CloudSimPlus simulation;

    private VmAllocationPolicyMigrationRandomFit allocationPolicy;

    private List<Host> hostList;

    public static void main(String[] args) {
        new MigrationRandomFitPolicy();
    }

    /**
     * O construtor funciona como o "main real" da simulação.
     * Toda a montagem e execução acontecem aqui.
     */
    private MigrationRandomFitPolicy() {
        Log.setLevel(Level.INFO);

        if (Config.Host.PES.length != Config.Host.RAM.length) {
            throw new IllegalStateException(
                "The length of arrays Config.Host.PES and Config.Host.RAM must match."
            );
        }

        System.out.println(
            "Starting " + getClass().getSimpleName()
        );

        // Motor de eventos discretos:
        // o clock representa tempo simulado.
        simulation = new CloudSimPlus();

        // Cria a infraestrutura física e instala a política.
        final Datacenter datacenter0 = createDatacenter();

        /*
         * Registra o resultado final das migrações.
         *
         * Esse listener informa ao MigrationTracker se uma
         * migração iniciada terminou com sucesso ou falha.
         */
        datacenter0.addOnVmMigrationFinishListener(
            migrationTracker::recordResult
        );

        // Broker responsável por submeter VMs e Cloudlets.
        broker = new DatacenterBrokerSimple(simulation);

        createAndSubmitVms(broker);
        createAndSubmitCloudlets(broker);

        broker.addOnVmsCreatedListener(
            this::onVmsCreatedListener
        );

        // Executa a simulação até não existirem mais eventos.
        simulation.start();

        final List<Cloudlet> finishedList =
            broker.getCloudletFinishedList();

        final Comparator<Cloudlet> cloudletComparator =
            comparingLong(
                (Cloudlet c) ->
                    c.getVm().getHost().getId()
            )
            .thenComparingLong(
                c -> c.getVm().getId()
            )
            .thenComparingLong(
                Cloudlet::getId
            );

        finishedList.sort(cloudletComparator);

        /*
         * Exporta os CSVs específicos da estratégia:
         *
         * migration_random_fit_policy.csv
         * migration_random_fit_power.csv
         */
        try {
            final java.io.File resultsDir =
                new java.io.File("migrations_results");

            if (!resultsDir.exists()) {
                resultsDir.mkdirs();
            }

            final CsvTable csv = new CsvTable();

            csv.setPrintStream(
                new PrintStream(
                    new java.io.File(
                        "migrations_results/"
                        + "migration_random_fit_policy.csv"
                    )
                )
            );

            new CloudletsTableBuilder(
                finishedList,
                csv
            ).build();

            final CsvTable powerCsv =
                new CsvTable();

            powerCsv.setPrintStream(
                new PrintStream(
                    new java.io.File(
                        "migrations_results/"
                        + "migration_random_fit_power.csv"
                    )
                )
            );

            Shared.exportPowerConsumptionToCsv(
                hostList,
                simulation,
                powerCsv,
                "Random Fit"
            );

            // Também exibe a tabela no terminal.
            new CloudletsTableBuilder(
                finishedList
            ).build();

        } catch (final IOException e) {
            System.err.println(
                "Error writing CSV files: "
                + e.getMessage()
            );
        }

        System.out.printf(
            "%nHosts CPU usage History "
            + "(when the allocated MIPS is lower than "
            + "the requested, it is due to VM migration "
            + "overhead)%n"
        );

        hostList.stream()
            .filter(host -> host.getId() <= 2)
            .forEach(
                Shared::printHostStateHistory
            );

        /*
         * Mostra:
         *
         * Started
         * Completed
         * Successful
         * Failed
         * Unfinished
         */
        migrationTracker.printSummary();

        /*
         * Atualiza migration_counts.csv.
         */
        try {
            MigrationCsvWriter.writeOrUpdate(
                "random_fit",
                migrationTracker
            );
        } catch (final IOException e) {
            System.err.println(
                "Error writing migration counts CSV: "
                + e.getMessage()
            );
        }

        /*
         * Atualiza simulation_counts.csv.
         *
         * Registra quantas Cloudlets foram submetidas
         * e quantas realmente terminaram.
         */
        try {
            SimulationCsvWriter.writeOrUpdate(
                "random_fit",
                broker
            );
        } catch (final IOException e) {
            System.err.println(
                "Error writing simulation counts CSV: "
                + e.getMessage()
            );
        }

        Shared.printPowerConsumptionSummary(
            hostList,
            simulation
        );

        System.out.println(
            getClass().getSimpleName()
            + " finished!"
        );
    }

    /**
     * Registra o início real de uma migração.
     */
    private void startMigration(
            final VmHostEventInfo info) {

        migrationTracker.recordStart(info);

        Shared.startMigration(
            migrationTracker.getStarted(),
            simulation,
            info
        );
    }

    /**
     * Mantém o listener já utilizado pelo projeto
     * para exibir informações do término da migração.
     */
    private void finishMigration(
            final VmHostEventInfo info) {

        Shared.finishMigration(
            hostList,
            info
        );
    }

    /**
     * Cria exatamente uma Cloudlet para cada VM.
     *
     * Todas começam em 80% de CPU e aumentam
     * gradualmente até 100%.
     */
    public void createAndSubmitCloudlets(
            final DatacenterBroker broker) {

        final List<Cloudlet> list =
            new ArrayList<>(
                Config.VM.PES.length
            );

        final UtilizationModelDynamic um =
            Shared.createCpuUtilizationModel(
                Config.Cloudlet.INITIAL_CPU_PERCENTAGE,
                1
            );

        for (final Vm vm : vmList) {
            list.add(
                Shared.createCloudlet(
                    vm,
                    broker,
                    um
                )
            );
        }

        if (!list.isEmpty()) {
            list.get(
                list.size() - 1
            ).setUtilizationModelCpu(
                Shared.createCpuUtilizationModel(
                    Config.Cloudlet
                        .INITIAL_CPU_PERCENTAGE,
                    1
                )
            );
        }

        broker.submitCloudletList(list);

        System.out.println(
            "Created "
            + list.size()
            + " cloudlets "
            + "(including high-utilization ones "
            + "to trigger migrations)"
        );
    }

    /**
     * Cria as VMs e registra os listeners
     * relacionados às migrações.
     */
    public void createAndSubmitVms(
            final DatacenterBroker broker) {

        final List<Vm> list =
            new ArrayList<>(
                Config.VM.PES.length
            );

        for (final int pes : Config.VM.PES) {
            list.add(
                Shared.createVm(pes)
            );
        }

        vmList.addAll(list);

        broker.submitVmList(list);

        /*
         * Quando uma migração começa:
         *
         * MigrationTracker.recordStart()
         */
        list.forEach(
            vm ->
                vm.addOnMigrationStartListener(
                    this::startMigration
                )
        );

        /*
         * Listener já existente no projeto
         * para exibir informações no término.
         *
         * A contabilização de sucesso/falha
         * é feita pelo listener do Datacenter.
         */
        list.forEach(
            vm ->
                vm.addOnMigrationFinishListener(
                    this::finishMigration
                )
        );

        list.forEach(
            Vm::enableUtilizationStats
        );
    }

    /**
     * Cria os Hosts e instala a política Random Fit.
     *
     * - VmSelectionPolicyMinimumUtilization:
     *   decide qual VM será retirada de um Host
     *   sobrecarregado.
     *
     * - VmAllocationPolicyMigrationRandomFit:
     *   escolhe aleatoriamente um destino válido.
     */
    private Datacenter createDatacenter() {

        this.hostList = createHosts();

        System.out.println();

        /*
         * RANDOM FIT:
         *
         * entre os Hosts válidos, escolhe
         * aleatoriamente o destino da VM.
         */
        this.allocationPolicy =
            new VmAllocationPolicyMigrationRandomFit(
                new VmSelectionPolicyMinimumUtilization(),
                Config.Host
                    .OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION
            );

        this.allocationPolicy
            .setUnderUtilizationThreshold(
                Config.Host
                    .UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION
            );

        final Datacenter dc =
            new DatacenterSimple(
                simulation,
                hostList,
                allocationPolicy
            );

        for (final Host host : hostList) {
            System.out.printf(
                "# Created %s with %.0f MIPS "
                + "x %d PEs (%.0f total MIPS)%n",
                host,
                host.getMips(),
                host.getPesNumber(),
                host.getTotalMipsCapacity()
            );
        }

        /*
         * Atualiza/processa Hosts a cada
         * 1 segundo de tempo simulado.
         */
        dc.setSchedulingInterval(
            Config.Scheduling.INTERVAL
        )
        .setHostSearchRetryDelay(
            Config.Host.SEARCH_RETRY_DELAY
        );

        return dc;
    }

    /**
     * Instancia os Hosts definidos no Shared.Config.
     *
     * Nesta etapa mantemos o mesmo scheduler que
     * já estava sendo usado pelo Random Fit.
     */
    private List<Host> createHosts() {

        final List<Host> list =
            new ArrayList<>(
                Config.Host.PES.length
            );

        for (
            int i = 0;
            i < Config.Host.PES.length;
            i++
        ) {

            final int pes =
                Config.Host.PES[i];

            final long ram =
                Config.Host.RAM[i];

            list.add(
                Shared.createHost(
                    pes,
                    ram
                )
            );
        }

        return list;
    }

    /**
     * Executado quando todas as VMs submetidas
     * pelo Broker foram criadas.
     *
     * O listener de clock abaixo apenas imprime
     * Hosts sobrecarregados nos primeiros segundos.
     * Ele não solicita migração diretamente.
     */
    private void onVmsCreatedListener(
            final DatacenterBrokerEventInfo info) {

        System.out.printf(
            "# All %d VMs submitted to the broker "
            + "have been created.%n",
            broker
                .getVmCreatedList()
                .size()
        );

        simulation.addOnClockTickListener(
            clock -> {

                if (
                    clock.getTime() > 0
                    && clock.getTime() <= 2
                ) {

                    System.out.printf(
                        "%.2f: Checking hosts for "
                        + "potential VM migrations...%n",
                        clock.getTime()
                    );

                    for (
                        final Host host :
                        hostList
                    ) {

                        final double cpuUtilization =
                            host.getCpuPercentUtilization();

                        if (
                            cpuUtilization
                            > Config.Host
                                .OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION
                        ) {

                            System.out.printf(
                                "%.2f: Host %d is overloaded "
                                + "with %.1f%% CPU utilization "
                                + "(threshold: %.1f%%)%n",
                                clock.getTime(),
                                host.getId(),
                                cpuUtilization * 100,
                                Config.Host
                                    .OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION
                                    * 100
                            );
                        }
                    }
                }
            }
        );

        broker.removeOnVmsCreatedListener(
            info.getListener()
        );

        vmList.forEach(
            vm ->
                Shared.showVmAllocatedMips(
                    vm,
                    vm.getHost(),
                    info.getTime()
                )
        );

        System.out.println();

        hostList.forEach(
            host ->
                Shared.showHostAllocatedMips(
                    info.getTime(),
                    host
                )
        );

        System.out.println();
    }
}