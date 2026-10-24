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

    /**
     * Índice usado apenas durante a alocação inicial.
     *
     * Replica o comportamento da implementação nativa
     * VmAllocationPolicyMigrationFirstFitStaticThreshold.
     */
    private int initialLastHostIndex;

    public VmAllocationPolicyMigrationRandomFit(
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
         * Se a VM ainda não foi criada, estamos na
         * alocação inicial.
         *
         * Nesse momento usamos First Fit para que
         * Random, Worst, Best e First partam da mesma
         * distribuição inicial.
         */
        if (!vm.isCreated()) {

            return findInitialHostFirstFit(
                    predicate
            );
        }

        /*
         * Se a VM já existe, estamos buscando um
         * novo Host para uma realocação/migração.
         *
         * O predicate recebido do CloudSim Plus
         * já verifica:
         *
         * - se o Host é diferente do atual;
         * - se o Host possui recursos para a VM;
         * - se o Host não ficará sobrecarregado;
         * - outras restrições do processo de migração.
         *
         * Por isso não é necessário chamar
         * host.isSuitableForVm(vm) novamente.
         */
        final List<Host> suitableHosts =
                getHostList()
                        .stream()
                        .filter(predicate)
                        .toList();

        if (suitableHosts.isEmpty()) {

            return Optional.empty();
        }

        /*
         * Random Fit:
         * seleciona aleatoriamente um dos Hosts
         * considerados válidos pelo CloudSim Plus.
         */
        final int randomIndex =
                random.nextInt(
                        suitableHosts.size()
                );

        return Optional.of(
                suitableHosts.get(
                        randomIndex
                )
        );
    }

    /**
     * Replica a lógica da política nativa
     * VmAllocationPolicyMigrationFirstFitStaticThreshold
     * exclusivamente para a alocação inicial.
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

            final Host host =
                    hosts.get(
                            initialLastHostIndex
                    );

            if (predicate.test(host)) {

                return Optional.of(
                        host
                );
            }

            initialLastHostIndex =
                    (initialLastHostIndex + 1)
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
    protected List<? extends Vm>
            getVmsToMigrateFromUnderUtilizedHost(
                    final Host host) {

        return new ArrayList<>(
                host.getMigratableVms()
        );
    }
}