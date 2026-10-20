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
 * 8) ao terminar, gera CSV de Cloudlets e CSV de energia.
 *
 * RANDOM FIT:
 * Entre os Hosts candidatos válidos (que comportam a VM), escolhe
 * aleatoriamente um destino. Isso traz imprevisibilidade ao processo,
 * podendo gerar resultados diferentes a cada execução (a menos que a
 * semente do gerador aleatório seja fixada).
 */
public final class MigrationRandomFitPolicy {

    // Lista local usada para criar Cloudlets e registrar listeners nas VMs.
    private final List<Vm> vmList = new ArrayList<>();
    private final DatacenterBrokerSimple broker;

    private final CloudSimPlus simulation;
    private VmAllocationPolicyMigrationRandomFit allocationPolicy;
    private List<Host> hostList;

    // Array de 1 posição é usado como um "inteiro mutável" para Shared.startMigration().
    private final int[] migrationsNumber = {0};

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
            throw new IllegalStateException("The length of arrays Config.Host.PES and Config.Host.RAM must match.");
        }

        System.out.println("Starting " + getClass().getSimpleName());
        // Motor de eventos discretos: o clock aqui é tempo SIMULADO, não tempo de CPU real.
        simulation = new CloudSimPlus();

        @SuppressWarnings("unused")
        // Cria a infraestrutura física e instala a política de migração.
        final Datacenter datacenter0 = createDatacenter();
        // Broker representa o usuário/orquestrador que submete VMs e Cloudlets.
        broker = new DatacenterBrokerSimple(simulation);
        createAndSubmitVms(broker);
        createAndSubmitCloudlets(broker);

        broker.addOnVmsCreatedListener(this::onVmsCreatedListener);

        // A partir daqui o CloudSim Plus processa os eventos até não haver mais trabalho.
        simulation.start();

        final List<Cloudlet> finishedList = broker.getCloudletFinishedList();
        final Comparator<Cloudlet> cloudletComparator =
            comparingLong((Cloudlet c) -> c.getVm().getHost().getId())
                .thenComparingLong(c -> c.getVm().getId())
                .thenComparingLong(Cloudlet::getId);
        finishedList.sort(cloudletComparator);

        try {
            java.io.File resultsDir = new java.io.File("migrations_results");
            if (!resultsDir.exists()) {
                resultsDir.mkdirs();
            }

            CsvTable csv = new CsvTable();
            csv.setPrintStream(new PrintStream(new java.io.File("migrations_results/migration_random_fit_policy.csv")));
            new CloudletsTableBuilder(finishedList, csv).build();

            CsvTable powerCsv = new CsvTable();
            powerCsv.setPrintStream(new PrintStream(new java.io.File("migrations_results/migration_random_fit_power.csv")));
            Shared.exportPowerConsumptionToCsv(hostList, simulation, powerCsv, "Random Fit");

            new CloudletsTableBuilder(finishedList).build();
        } catch (IOException e) {
            System.err.println("Error writing CSV files: " + e.getMessage());
        }

        System.out.printf(
            "%nHosts CPU usage History (when the allocated MIPS is lower than the requested, it is due to VM migration overhead)%n");

        hostList.stream().filter(h -> h.getId() <= 2).forEach(Shared::printHostStateHistory);
        System.out.printf("Number of VM migrations: %d%n", migrationsNumber[0]);

        Shared.printPowerConsumptionSummary(hostList, simulation);

        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void startMigration(final VmHostEventInfo info) {
        Shared.startMigration(migrationsNumber, simulation, info);
    }

    private void finishMigration(final VmHostEventInfo info) {
        Shared.finishMigration(hostList, info);
    }

    /**
     * Cria exatamente uma Cloudlet para cada VM criada anteriormente.
     * Todas começam em 80% de CPU e sobem até 100%.
     *
     * A última Cloudlet recebe um NOVO modelo dinâmico, mas com os mesmos
     * parâmetros 0,8 -> 1,0. Portanto ela não é mais "especial" que as outras.
     */
    public void createAndSubmitCloudlets(DatacenterBroker broker) {
        final List<Cloudlet> list = new ArrayList<>(Config.VM.PES.length);
        UtilizationModelDynamic um = Shared.createCpuUtilizationModel(Config.Cloudlet.INITIAL_CPU_PERCENTAGE, 1);

        for (Vm vm : vmList) {
            list.add(Shared.createCloudlet(vm, broker, um));
        }

        if (!list.isEmpty()) {
            list.get(list.size() - 1).setUtilizationModelCpu(
                Shared.createCpuUtilizationModel(Config.Cloudlet.INITIAL_CPU_PERCENTAGE, 1));
        }

        broker.submitCloudletList(list);
        System.out.println("Created " + list.size() + " cloudlets (including high-utilization ones to trigger migrations)");
    }

    /**
     * Converte cada entrada Config.VM.PES em uma VM e registra listeners de
     * início/fim de migração. Também habilita estatísticas de CPU por VM.
     */
    public void createAndSubmitVms(DatacenterBroker broker) {
        final List<Vm> list = new ArrayList<>(Config.VM.PES.length);
        for (final int pes : Config.VM.PES) {
            list.add(Shared.createVm(pes));
        }

        vmList.addAll(list);
        broker.submitVmList(list);

        list.forEach(vm -> vm.addOnMigrationStartListener(this::startMigration));
        list.forEach(vm -> vm.addOnMigrationFinishListener(this::finishMigration));
        list.forEach(vm -> vm.enableUtilizationStats());
    }

    /**
     * Cria os Hosts e instala a política que decide:
     * - quando um Host está acima/abaixo dos limiares;
     * - qual VM deve sair (MinimumUtilization);
     * - para qual Host essa VM deve ir (Random Fit).
     *
     * RANDOM FIT: entre os destinos válidos (que tenham recursos
     * suficientes), escolhe um Host ALEATORIAMENTE.
     */
    private Datacenter createDatacenter() {
        this.hostList = createHosts();
        System.out.println();

        // RANDOM FIT: entre os destinos válidos, escolhe um Host aleatório
        // para receber a VM migrada. Não há tentativa de consolidar nem
        // de espalhar; é puramente probabilístico.
        
        this.allocationPolicy = new VmAllocationPolicyMigrationRandomFit(new VmSelectionPolicyMinimumUtilization(), 
        Config.Host.OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION);
            // Quando um Host está sobrecarregado, seleciona primeiro uma VM
            // de menor utilização de CPU para migrar.

        this.allocationPolicy.setUnderUtilizationThreshold(Config.Host.UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION);

        // O Datacenter é a entidade que periodicamente atualiza os Hosts e consulta
        // a política de migração para obter um mapa VM -> Host destino.
        final Datacenter dc = new DatacenterSimple(simulation, hostList, allocationPolicy);
        for (Host host : hostList) {
            System.out.printf(
                "# Created %s with %.0f MIPS x %d PEs (%.0f total MIPS)%n",
                host, host.getMips(), host.getPesNumber(), host.getTotalMipsCapacity());
        }

        // Atualiza/processa em intervalos de 1 segundo de tempo simulado.
        dc.setSchedulingInterval(Config.Scheduling.INTERVAL)
            // Se uma busca de destino falhar, espera 60 s simulados antes de nova tentativa.
            .setHostSearchRetryDelay(Config.Host.SEARCH_RETRY_DELAY);

        return dc;
    }

    /**
     * Instancia os 100 Hosts definidos pelos vetores paralelos PES e RAM.
     * A validação no construtor garante que os dois vetores tenham mesmo tamanho.
     */
    private List<Host> createHosts() {
        final List<Host> list = new ArrayList<>(Config.Host.PES.length);

        for (int i = 0; i < Config.Host.PES.length; i++) {
            final int pes = Config.Host.PES[i];
            final long ram = Config.Host.RAM[i];
            list.add(Shared.createHostWithFixedScheduler(pes, ram));
        }

        return list;
    }

    /**
     * Executado uma única vez quando o Broker termina de criar as VMs.
     * O listener de clock adicionado aqui APENAS IMPRIME Hosts >80% nos
     * dois primeiros segundos; ele não solicita migração diretamente.
     */
    private void onVmsCreatedListener(final DatacenterBrokerEventInfo info) {
        System.out.printf("# All %d VMs submitted to the broker have been created.%n",
            broker.getVmCreatedList().size());

        // Ensure migration threshold is set and applied

        // Add a special listener that will check for possible VM migrations
        simulation.addOnClockTickListener(clock -> {
            if (clock.getTime() > 0 && clock.getTime() <= 2) {
                System.out.printf("%.2f: Checking hosts for potential VM migrations...%n", clock.getTime());

                // Manually check for overloaded hosts
                for (Host host : hostList) {
                    double cpuUtilization = host.getCpuPercentUtilization();
                    if (cpuUtilization > Config.Host.OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION) {
                        System.out.printf("%.2f: Host %d is overloaded with %.1f%% CPU utilization (threshold: %.1f%%)%n",
                            clock.getTime(), host.getId(), cpuUtilization * 100,
                            Config.Host.OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION * 100);
                    }
                }
            }
        });

        broker.removeOnVmsCreatedListener(info.getListener());
        vmList.forEach(vm -> Shared.showVmAllocatedMips(vm, vm.getHost(), info.getTime()));

        System.out.println();
        hostList.forEach(host -> Shared.showHostAllocatedMips(info.getTime(), host));
        System.out.println();
    }
}