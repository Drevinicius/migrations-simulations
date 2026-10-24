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
            final double overUtilizationThreshold) {

        super(vmSelectionPolicy);

        this.overUtilizationThreshold =
                overUtilizationThreshold;
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
         * Alocação inicial.
         *
         * Enquanto a VM ainda não foi criada,
         * utilizamos First Fit para garantir a
         * mesma condição inicial das políticas
         * Best Fit e First Fit.
         */
        if (!vm.isCreated()) {

            return findInitialHostFirstFit(
                    predicate
            );
        }

        /*
         * A VM já existe.
         *
         * Neste ponto estamos escolhendo um novo
         * Host durante uma realocação/migração.
         *
         * O predicate já contém as validações
         * realizadas internamente pelo CloudSim Plus.
         */

        /*
         * Worst Fit:
         *
         * dentre os Hosts válidos, escolhe aquele
         * com a MAIOR quantidade absoluta de MIPS
         * disponíveis.
         */
        return getHostList()
                .stream()
                .filter(predicate)
                .max(
                        Comparator.comparingDouble(
                                Host::getTotalAvailableMips
                        )
                );
    }

    /**
     * Replica a lógica da política nativa
     * VmAllocationPolicyMigrationFirstFitStaticThreshold
     * exclusivamente durante a criação inicial
     * das VMs.
     */
    private Optional<Host> findInitialHostFirstFit(
            final Predicate<Host> predicate) {

        final List<Host> hosts =
                getHostList();

        final int hostCount =
                hosts.size();

        if (hostCount == 0) {

            return Optional.empty();
        }

        for (int i = 0; i < hostCount; i++) {

            final Host host =
                    hosts.get(
                            initialLastHostIndex
                    );

            /*
             * O predicate já verifica todos os
             * requisitos necessários para receber
             * a VM.
             */
            if (predicate.test(host)) {

                return Optional.of(
                        host
                );
            }

            /*
             * O Host atual não pode receber a VM.
             * Avança para o próximo Host.
             */
            initialLastHostIndex =
                    (initialLastHostIndex + 1)
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
    protected List<? extends Vm>
            getVmsToMigrateFromUnderUtilizedHost(
                    final Host host) {

        return new ArrayList<>(
                host.getMigratableVms()
        );
    }
}