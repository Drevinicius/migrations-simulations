package simulations;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationFirstFitStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * First Fit nativo com instrumentação do mapa de migrações planejadas.
 *
 * A lógica de seleção do CloudSim Plus não é alterada. A única diferença é
 * registrar no MigrationTracker o mapa VM -> Host retornado pelo planejamento.
 */
public final class TrackedVmAllocationPolicyMigrationFirstFitStaticThreshold
        extends VmAllocationPolicyMigrationFirstFitStaticThreshold {

    private final MigrationTracker migrationTracker;

    public TrackedVmAllocationPolicyMigrationFirstFitStaticThreshold(
            final VmSelectionPolicy vmSelectionPolicy,
            final double overUtilizationThreshold,
            final MigrationTracker migrationTracker) {

        super(vmSelectionPolicy, overUtilizationThreshold);
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
}
