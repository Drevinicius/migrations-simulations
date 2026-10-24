package simulations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

/**
 * Estratégia de migração Worst Fit / Worst First personalizada:
 * Entre todos os Hosts que atendem às condições, seleciona o que possui
 * o maior espaço de MIPS livres (total available MIPS).
 */
public class VmAllocationPolicyMigrationWorstFit extends VmAllocationPolicyMigrationAbstract {

    private final double overUtilizationThreshold;

    public VmAllocationPolicyMigrationWorstFit(
            final VmSelectionPolicy vmSelectionPolicy,
            final double overUtilizationThreshold) {
        super(vmSelectionPolicy);
        this.overUtilizationThreshold = overUtilizationThreshold;
    }

    @Override
    public double getOverUtilizationThreshold(Host host) {
        return overUtilizationThreshold;
    }

    @Override
    protected Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> suitableHosts = getHostList().stream()
                .filter(host -> host.isSuitableForVm(vm))
                .filter(predicate)
                .toList();

        if (suitableHosts.isEmpty()) {
            return Optional.empty();
        }

        return suitableHosts.stream()
                .max(Comparator.comparingDouble(Host::getTotalAvailableMips));
    }

    @Override
    protected List<? extends Vm> getVmsToMigrateFromUnderUtilizedHost(final Host host) {
        return new ArrayList<>(host.getMigratableVms());
    }
}