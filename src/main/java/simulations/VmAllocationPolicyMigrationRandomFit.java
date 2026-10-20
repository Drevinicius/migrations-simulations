package simulations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import java.util.function.Predicate;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

/**
 * Estratégia de migração Random Fit personalizada:
 * Entre todos os Hosts que atendem às condições, seleciona aleatoriamente um.
 */
public class VmAllocationPolicyMigrationRandomFit extends VmAllocationPolicyMigrationAbstract {

    private final Random random = new Random();
    private final double overUtilizationThreshold;

    public VmAllocationPolicyMigrationRandomFit(
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
        // Use predicate para filtrar os hosts e depois selecionar aleatoriamente
        List<Host> suitableHosts = getHostList().stream()
            .filter(host -> host.isSuitableForVm(vm))
            .filter(predicate)
            .filter(host -> host.isSuitableForVm(vm))
            .toList();
    
        if (suitableHosts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(suitableHosts.get(random.nextInt(suitableHosts.size())));
    }
        /**
     * Sobrescreve para retornar uma lista MUTÁVEL de VMs migráveis.
     *
     * A classe pai (VmAllocationPolicyMigrationAbstract) chama .sort() na lista
     * retornada por este método. A implementação padrão usa Host.getMigratableVms(),
     * que em versões recentes do CloudSim Plus retorna uma lista imutável
     * (gerada por Stream.toList()), causando UnsupportedOperationException.
     *
     * Ao retornar new ArrayList<>(...), garantimos que a ordenação funcione.
     */
    @Override
    protected List<? extends Vm> getVmsToMigrateFromUnderUtilizedHost(final Host host) {
        return new ArrayList<>(host.getMigratableVms());
    }
}