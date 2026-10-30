package simulations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

/**
 * Estratégia de migração Random Fit personalizada.
 *
 * A alocação inicial das VMs replica o comportamento do First Fit nativo,
 * garantindo a mesma condição inicial usada pelas políticas Best Fit e
 * First Fit neste experimento.
 *
 * Depois que a VM já foi criada, a escolha de um novo Host durante a
 * migração é feita aleatoriamente entre os Hosts válidos fornecidos pelo
 * predicate do CloudSim Plus.
 */
public class VmAllocationPolicyMigrationRandomFit
        extends VmAllocationPolicyMigrationAbstract {

    private final Random random = new Random();

    private final double overUtilizationThreshold;

    private final MigrationTracker migrationTracker;

    /**
     * Índice usado apenas durante a alocação inicial.
     *
     * Replica o comportamento da implementação nativa
     * VmAllocationPolicyMigrationFirstFitStaticThreshold.
     */
    private int initialLastHostIndex;

    public VmAllocationPolicyMigrationRandomFit(
            final VmSelectionPolicy vmSelectionPolicy,
            final double overUtilizationThreshold,
            final MigrationTracker migrationTracker) {

        super(vmSelectionPolicy);

        this.overUtilizationThreshold = overUtilizationThreshold;
        this.migrationTracker = Objects.requireNonNull(migrationTracker);
    }

    @Override
    public Map<Vm, Host> getOptimizedAllocationMap(
            final List<? extends Vm> vmList) {

        final Map<Vm, Host> migrationMap =
                super.getOptimizedAllocationMap(vmList);

        migrationTracker.recordPlanned(migrationMap);
        return migrationMap;
    }

    @Override
    public double getOverUtilizationThreshold(
            final Host host) {

        return overUtilizationThreshold;
    }
    
    @Override
    protected Optional<Host> findHostForVmInternal(
            final Vm vm,
            final Predicate<Host> predicate) {

        /*
         * Apenas alocação inicial.
         */
        if (vm.getHost() == null || vm.getHost().getId() < 0) {
            return findInitialHostFirstFit(predicate);
        }

        /*
         * A VM já possui Host.
         * Agora aplicamos Random Fit.
         */
        final List<Host> suitableHosts = getHostList()
                .stream()
                .filter(predicate)
                .toList();

        System.out.printf(
                "%n[RANDOM MIGRATION] VM %d | sourceHost=%d | candidates=%d%n",
                vm.getId(),
                vm.getHost().getId(),
                suitableHosts.size());

        System.out.printf(
                "[RANDOM MIGRATION] Candidates: %s%n",
                suitableHosts.stream()
                        .map(host -> String.format(
                                "%d(availableMips=%.0f)",
                                host.getId(),
                                host.getTotalAvailableMips()))
                        .toList());

        if (suitableHosts.isEmpty()) {
            return Optional.empty();
        }

        final int randomIndex = random.nextInt(
                suitableHosts.size());

        final Host selectedHost = suitableHosts.get(
                randomIndex);

        System.out.printf(
                "[RANDOM MIGRATION] VM %d | Host %d -> Host %d | selectedIndex=%d/%d%n%n",
                vm.getId(),
                vm.getHost().getId(),
                selectedHost.getId(),
                randomIndex,
                suitableHosts.size() - 1);

        return Optional.of(
                selectedHost);
    }

    /**
     * Replica a lógica da política nativa
     * VmAllocationPolicyMigrationFirstFitStaticThreshold
     * exclusivamente para a alocação inicial.
     */
    private Optional<Host> findInitialHostFirstFit(
            final Predicate<Host> predicate) {

        final List<Host> hosts = getHostList();

        final int hostCount = hosts.size();

        if (hostCount == 0) {

            return Optional.empty();
        }

        /*
         * Procura a partir do último Host utilizado.
         *
         * Enquanto o Host atual puder receber novas
         * VMs, ele continua sendo utilizado.
         *
         * Quando deixa de atender ao predicate,
         * o algoritmo avança para o próximo Host.
         */
        for (int i = 0; i < hostCount; i++) {

            final Host host = hosts.get(
                    initialLastHostIndex);

            if (predicate.test(host)) {

                return Optional.of(
                        host);
            }

            initialLastHostIndex = (initialLastHostIndex + 1)
                    % hostCount;
        }

        return Optional.empty();
    }

    /**
     * Retorna uma lista mutável de VMs migráveis.
     *
     * A classe pai ordena a lista retornada.
     * Em versões do CloudSim Plus em que
     * Host.getMigratableVms() retorna uma lista
     * imutável, a cópia em ArrayList evita
     * UnsupportedOperationException.
     */
    @Override
    protected List<? extends Vm> getVmsToMigrateFromUnderUtilizedHost(
            final Host host) {

        return new ArrayList<>(
                host.getMigratableVms());
    }
}