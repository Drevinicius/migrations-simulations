package simulations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

/**
 * Estratégia de migração Worst Fit personalizada.
 *
 * A alocação inicial das VMs replica o comportamento do First Fit nativo,
 * garantindo a mesma condição inicial usada pelas políticas Best Fit e
 * First Fit neste experimento.
 *
 * Depois que a VM já foi criada, a escolha de um novo Host durante a
 * migração usa Worst Fit: entre os Hosts válidos, seleciona aquele com a
 * maior quantidade absoluta de MIPS disponíveis.
 */
public class VmAllocationPolicyMigrationWorstFit
        extends VmAllocationPolicyMigrationAbstract {

    private final double overUtilizationThreshold;

    private final MigrationTracker migrationTracker;

    /**
     * Índice utilizado exclusivamente durante
     * a alocação inicial.
     *
     * Replica o comportamento da implementação
     * nativa First Fit.
     */
    private int initialLastHostIndex;

    public VmAllocationPolicyMigrationWorstFit(
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
         *
         * Uma VM ainda não alocada possui Host inválido
         * (ID negativo).
         */
        if (vm.getHost() == null || vm.getHost().getId() < 0) {
            return findInitialHostFirstFit(predicate);
        }

        /*
         * A VM já possui Host de origem.
         * Portanto, estamos escolhendo destino
         * para uma realocação/migração.
         */
        final List<Host> suitableHosts = getHostList()
                .stream()
                .filter(predicate)
                .toList();

        System.out.printf(
                "%n[WORST MIGRATION] VM %d | sourceHost=%d | candidates=%d%n",
                vm.getId(),
                vm.getHost().getId(),
                suitableHosts.size());

        System.out.printf(
                "[WORST MIGRATION] Candidates: %s%n",
                suitableHosts.stream()
                        .map(host -> String.format(
                                "%d(availableMips=%.0f)",
                                host.getId(),
                                host.getTotalAvailableMips()))
                        .toList());

        if (suitableHosts.isEmpty()) {
            return Optional.empty();
        }

        final Optional<Host> selectedHost = suitableHosts.stream()
                .max(
                        Comparator.comparingDouble(
                                Host::getTotalAvailableMips));

        selectedHost.ifPresent(host -> System.out.printf(
                "[WORST MIGRATION] VM %d | Host %d -> Host %d | availableMips=%.0f%n%n",
                vm.getId(),
                vm.getHost().getId(),
                host.getId(),
                host.getTotalAvailableMips()));

        return selectedHost;
    }

    /**
     * Replica a lógica da política nativa
     * VmAllocationPolicyMigrationFirstFitStaticThreshold
     * exclusivamente durante a criação inicial
     * das VMs.
     */
    private Optional<Host> findInitialHostFirstFit(
            final Predicate<Host> predicate) {

        final List<Host> hosts = getHostList();

        final int hostCount = hosts.size();

        if (hostCount == 0) {

            return Optional.empty();
        }

        for (int i = 0; i < hostCount; i++) {

            final Host host = hosts.get(
                    initialLastHostIndex);

            /*
             * O predicate já verifica todos os
             * requisitos necessários para receber
             * a VM.
             */
            if (predicate.test(host)) {

                return Optional.of(
                        host);
            }

            /*
             * O Host atual não pode receber a VM.
             * Avança para o próximo Host.
             */
            initialLastHostIndex = (initialLastHostIndex + 1)
                    % hostCount;
        }

        return Optional.empty();
    }

    /**
     * Retorna uma lista mutável das VMs migráveis.
     *
     * Isso permite que a classe pai realize
     * ordenações sem gerar
     * UnsupportedOperationException.
     */
    @Override
    protected List<? extends Vm> getVmsToMigrateFromUnderUtilizedHost(
            final Host host) {

        return new ArrayList<>(
                host.getMigratableVms());
    }
}