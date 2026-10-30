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
 * Simulação completa usando a política Worst First / Worst Fit
 * com limiares estáticos.
 */
public final class MigrationWorstFitPolicy {

    private final List<Vm> vmList = new ArrayList<>();

    private final DatacenterBrokerSimple broker;

    private final CloudSimPlus simulation;

    private VmAllocationPolicyMigrationWorstFit allocationPolicy;

    private List<Host> hostList;

    private final MigrationTracker migrationTracker = new MigrationTracker();

    public static void main(final String[] args) {
        new MigrationWorstFitPolicy();
    }

    private MigrationWorstFitPolicy() {

        Log.setLevel(Level.INFO);

        if (Config.Host.PES.length != Config.Host.RAM.length) {
            throw new IllegalStateException(
                    "The length of arrays Config.Host.PES and Config.Host.RAM must match.");
        }

        System.out.println(
                "Starting " + getClass().getSimpleName());

        simulation = new CloudSimPlus();

        final Datacenter datacenter0 = createDatacenter();

        /*
         * Registra o resultado final das migrações.
         *
         * Permite ao MigrationTracker diferenciar:
         * - concluídas com sucesso;
         * - concluídas com falha.
         */
        datacenter0.addOnVmMigrationFinishListener(
                migrationTracker::recordResult);

        broker = new DatacenterBrokerSimple(
                simulation);

        createAndSubmitVms(
                broker);

        createAndSubmitCloudlets(
                broker);

        broker.addOnVmsCreatedListener(
                this::onVmsCreatedListener);

        simulation.start();

        final List<Cloudlet> finishedList = broker.getCloudletFinishedList();

        final Comparator<Cloudlet> cloudletComparator = comparingLong(
                (Cloudlet c) -> c.getVm()
                        .getHost()
                        .getId())
                .thenComparingLong(
                        c -> c.getVm()
                                .getId())
                .thenComparingLong(
                        Cloudlet::getId);

        finishedList.sort(
                cloudletComparator);

        /*
         * Exporta:
         *
         * migration_worst_first_policy.csv
         * migration_worst_first_power.csv
         */
        try {

            final java.io.File resultsDir = new java.io.File(
                    "migrations_results");

            if (!resultsDir.exists()) {
                resultsDir.mkdirs();
            }

            final CsvTable csv = new CsvTable();

            csv.setPrintStream(
                    new PrintStream(
                            new java.io.File(
                                    "migrations_results/"
                                            + "migration_worst_first_policy.csv")));

            new CloudletsTableBuilder(
                    finishedList,
                    csv).build();

            final CsvTable powerCsv = new CsvTable();

            powerCsv.setPrintStream(
                    new PrintStream(
                            new java.io.File(
                                    "migrations_results/"
                                            + "migration_worst_first_power.csv")));

            Shared.exportPowerConsumptionToCsv(
                    hostList,
                    simulation,
                    powerCsv,
                    "Worst First");

            /*
             * Também mostra os resultados das
             * Cloudlets no terminal.
             */
            new CloudletsTableBuilder(
                    finishedList).build();

        } catch (final IOException e) {

            System.err.println(
                    "Error writing CSV files: "
                            + e.getMessage());
        }

        System.out.printf(
                "%nHosts CPU usage History "
                        + "(when the allocated MIPS is lower "
                        + "than the requested, it is due to "
                        + "VM migration overhead)%n");

        hostList.stream()
                .filter(
                        host -> host.getId() <= 2)
                .forEach(
                        Shared::printHostStateHistory);

        /*
         * Exibe:
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
                    "worst_first",
                    migrationTracker);

        } catch (final IOException e) {

            System.err.println(
                    "Error writing migration counts CSV: "
                            + e.getMessage());
        }

        /*
         * Atualiza simulation_counts.csv.
         *
         * Registra:
         * - Cloudlets submetidas;
         * - Cloudlets finalizadas.
         */
        try {
            MigrationMovementCsvWriter.writeOrUpdate("worst_first", migrationTracker);
        } catch (final IOException e) {
            System.err.println("Error writing migration movements CSV: " + e.getMessage());
        }

try {

            SimulationCsvWriter.writeOrUpdate(
                    "worst_first",
                    broker);

        } catch (final IOException e) {

            System.err.println(
                    "Error writing simulation counts CSV: "
                            + e.getMessage());
        }

        Shared.printPowerConsumptionSummary(
                hostList,
                simulation);

        System.out.println(
                getClass()
                        .getSimpleName()
                        + " finished!");
    }

    /**
     * Registra o início de uma migração.
     */
    private void startMigration(
            final VmHostEventInfo info) {

        migrationTracker.recordStart(
                info);

        Shared.startMigration(
                migrationTracker.getStarted(),
                simulation,
                info);
    }

    /**
     * Mantém a saída padrão de finalização
     * utilizada pelo projeto.
     *
     * A contabilização de sucesso ou falha
     * fica a cargo do listener do Datacenter.
     */
    private void finishMigration(
            final VmHostEventInfo info) {

        Shared.finishMigration(
                hostList,
                info);
    }

    /**
     * Cria uma Cloudlet para cada VM.
     */
    public void createAndSubmitCloudlets(
            final DatacenterBroker broker) {

        final List<Cloudlet> list = new ArrayList<>(
                Config.VM.PES.length);

        final UtilizationModelDynamic utilizationModel = Shared.createCpuUtilizationModel(
                Config.Cloudlet.INITIAL_CPU_PERCENTAGE,
                1);

        for (final Vm vm : vmList) {

            list.add(
                    Shared.createCloudlet(
                            vm,
                            broker,
                            utilizationModel));
        }

        if (!list.isEmpty()) {

            list.get(
                    list.size() - 1).setUtilizationModelCpu(
                            Shared.createCpuUtilizationModel(
                                    Config.Cloudlet.INITIAL_CPU_PERCENTAGE,
                                    1));
        }

        broker.submitCloudletList(
                list);

        System.out.println(
                "Created "
                        + list.size()
                        + " cloudlets "
                        + "(including high-utilization ones "
                        + "to trigger migrations)");
    }

    /**
     * Cria as VMs e registra os listeners
     * relacionados às migrações.
     */
    public void createAndSubmitVms(
            final DatacenterBroker broker) {

        final List<Vm> list = new ArrayList<>(
                Config.VM.PES.length);

        for (final int pes : Config.VM.PES) {

            list.add(
                    Shared.createVm(
                            pes));
        }

        vmList.addAll(
                list);

        broker.submitVmList(
                list);

        /*
         * Registra quando uma migração começa.
         */
        list.forEach(
                vm -> vm.addOnMigrationStartListener(
                        this::startMigration));

        /*
         * Mantém o listener padrão de término
         * utilizado pelo projeto.
         */
        list.forEach(
                vm -> vm.addOnMigrationFinishListener(
                        this::finishMigration));

        list.forEach(
                Vm::enableUtilizationStats);
    }

    /**
     * Cria o Datacenter e instala a implementação
     * customizada da política Worst Fit.
     */
    private Datacenter createDatacenter() {

        hostList = createHosts();

        System.out.println();

        allocationPolicy = new VmAllocationPolicyMigrationWorstFit(
                new VmSelectionPolicyMinimumUtilization(),
                Config.Host.OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION,
                migrationTracker);

        allocationPolicy
                .setUnderUtilizationThreshold(
                        Config.Host.UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION);

        final Datacenter dc = new DatacenterSimple(
                simulation,
                hostList,
                allocationPolicy);

        for (final Host host : hostList) {

            System.out.printf(
                    "# Created %s with %.0f MIPS "
                            + "x %d PEs (%.0f total MIPS)%n",
                    host,
                    host.getMips(),
                    host.getPesNumber(),
                    host.getTotalMipsCapacity());
        }

        dc.setSchedulingInterval(
                Config.Scheduling.INTERVAL)
                .setHostSearchRetryDelay(
                        Config.Host.SEARCH_RETRY_DELAY);

        return dc;
    }

    /**
     * Mantém o scheduler corrigido já utilizado
     * pela implementação Worst Fit atual.
     */
    private List<Host> createHosts() {

        final List<Host> list = new ArrayList<>(
                Config.Host.PES.length);

        for (int i = 0; i < Config.Host.PES.length; i++) {

            final int pes = Config.Host.PES[i];

            final long ram = Config.Host.RAM[i];

            list.add(
                    Shared.createHostWithFixedScheduler(
                            pes,
                            ram));
        }

        return list;
    }

    private void printInitialVmDistribution() {

        System.out.println();
        System.out.println("===== INITIAL VM DISTRIBUTION =====");

        long hostsWithVms = hostList.stream()
                .filter(host -> !host.getVmList().isEmpty())
                .count();

        System.out.printf(
                "Hosts with VMs: %d%n",
                hostsWithVms);

        hostList.stream()
                .filter(host -> !host.getVmList().isEmpty())
                .sorted(
                        Comparator.comparingLong(
                                Host::getId))
                .forEach(host -> {

                    System.out.printf(
                            "Host %d -> VMs: %d | "
                                    + "Total MIPS: %.0f | "
                                    + "Allocated MIPS: %.0f | "
                                    + "Available MIPS: %.0f%n",
                            host.getId(),
                            host.getVmList().size(),
                            host.getTotalMipsCapacity(),
                            host.getTotalAllocatedMips(),
                            host.getTotalAvailableMips());
                });

        System.out.println("===================================");
    }

    /**
     * Executado após a criação das VMs.
     *
     * O listener de clock apenas mostra Hosts
     * sobrecarregados nos primeiros segundos.
     */
    private void onVmsCreatedListener(
            final DatacenterBrokerEventInfo info) {

        System.out.printf(
                "# All %d VMs submitted to the broker "
                        + "have been created.%n",
                broker
                        .getVmCreatedList()
                        .size());

        simulation.addOnClockTickListener(
                clock -> {

                    if (clock.getTime() > 0
                            && clock.getTime() <= 2) {

                        System.out.printf(
                                "%.2f: Checking hosts for "
                                        + "potential VM migrations...%n",
                                clock.getTime());

                        for (final Host host : hostList) {

                            final double cpuUtilization = host.getCpuPercentUtilization();

                            if (cpuUtilization > Config.Host.OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION) {

                                System.out.printf(
                                        "%.2f: Host %d is overloaded "
                                                + "with %.1f%% CPU utilization "
                                                + "(threshold: %.1f%%)%n",
                                        clock.getTime(),
                                        host.getId(),
                                        cpuUtilization * 100,
                                        Config.Host.OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION
                                                * 100);
                            }
                        }
                    }
                });

        broker.removeOnVmsCreatedListener(
                info.getListener());

        vmList.forEach(
                vm -> Shared.showVmAllocatedMips(
                        vm,
                        vm.getHost(),
                        info.getTime()));

        System.out.println();

        hostList.forEach(
                host -> Shared.showHostAllocatedMips(
                        info.getTime(),
                        host));

        System.out.println();
    }
}