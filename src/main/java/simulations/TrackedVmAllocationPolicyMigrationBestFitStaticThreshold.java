package simulations;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationBestFitStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Best Fit nativo com:
 *     instrumentação do mapa de migrações planejadas;
 *     proteção contra destinos cuja capacidade depende da saída de uma VM
 *     de um Host sobrecarregado no mesmo ciclo de planejamento.
 *
 * Durante {@link #getOptimizedAllocationMap(List)}, os Hosts que estão
 * sobrecarregados no início do ciclo são considerados fontes potenciais das
 * migrações daquele lote e ficam temporariamente bloqueados como destino.
 * Isso evita que o planejador use uma capacidade que só existirá depois que
 * outra migração do mesmo lote terminar.
 *
 * Fora do planejamento de migrações, inclusive durante a alocação inicial
 * das VMs, o comportamento do Best Fit permanece exatamente o nativo.
 */
public final class TrackedVmAllocationPolicyMigrationBestFitStaticThreshold
        extends VmAllocationPolicyMigrationBestFitStaticThreshold {

    private final MigrationTracker migrationTracker;

    /**
     * IDs dos Hosts que não podem ser usados como destino enquanto o mapa de
     * migrações do ciclo atual está sendo construído.
     *
     * Fora de getOptimizedAllocationMap este conjunto fica vazio.
     */
    private Set<Long> blockedTargetHostIds = Collections.emptySet();

    public TrackedVmAllocationPolicyMigrationBestFitStaticThreshold(
            final VmSelectionPolicy vmSelectionPolicy,
            final double overUtilizationThreshold,
            final MigrationTracker migrationTracker) {

        super(vmSelectionPolicy, overUtilizationThreshold);
        this.migrationTracker = Objects.requireNonNull(migrationTracker);
    }

    @Override
    public Map<Vm, Host> getOptimizedAllocationMap(
            final List<? extends Vm> vmList) {

        /*
         * Captura o estado REAL antes que o CloudSim Plus comece a remover VMs
         * temporariamente para avaliar o mapa de migração.
         *
         * Um Host sobrecarregado é uma possível origem de migração neste lote.
         * Se ele fosse aceito como destino de outra VM, a decisão poderia
         * depender de recursos que só serão liberados futuramente.
         */
        blockedTargetHostIds = getHostList().stream()
                .filter(this::isOverloaded)
                .map(Host::getId)
                .collect(Collectors.toCollection(HashSet::new));

        try {
            final Map<Vm, Host> migrationMap =
                    super.getOptimizedAllocationMap(vmList);

            migrationTracker.recordPlanned(migrationMap);
            return migrationMap;
        } finally {
            /*
             * A restrição existe somente durante o planejamento da migração.
             * Isso garante que a alocação inicial e buscas feitas fora desse
             * ciclo continuem usando o comportamento Best Fit nativo.
             */
            blockedTargetHostIds = Collections.emptySet();
        }
    }

    /**
     * Mantém o critério Best Fit nativo e apenas acrescenta à filtragem de
     * candidatos a regra de que Hosts-fontes deste lote não podem ser destino.
     */
    @Override
    protected Optional<Host> findHostForVmInternal(
            final Vm vm,
            final Predicate<Host> predicate) {

        if (blockedTargetHostIds.isEmpty()) {
            return super.findHostForVmInternal(vm, predicate);
        }

        final Predicate<Host> destinationIsStable =
                host -> !blockedTargetHostIds.contains(host.getId());

        return super.findHostForVmInternal(
                vm,
                predicate.and(destinationIsStable)
        );
    }
}
