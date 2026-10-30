package simulations;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.listeners.DatacenterVmMigrationEventInfo;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.vms.Vm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rastreia o ciclo completo das migrações de VMs durante a simulação.
 *
 * O tracker diferencia quatro momentos:
 * - planejada: a política retornou a VM no mapa de otimização VM -> Host;
 * - iniciada: o Host de destino realmente aceitou a VM como migrating-in;
 * - concluída com sucesso;
 * - concluída com falha.
 *
 * Assim, uma migração planejada que nunca chega ao evento de início não é
 * perdida: ela permanece contabilizada como "planned but not started".
 */
public final class MigrationTracker {

    private int planned;
    private int started;
    private int successful;
    private int failed;

    /**
     * Quantidade de planejamentos ainda não convertidos em início real por VM.
     * O contador permite que a mesma VM seja planejada/migrada mais de uma vez.
     */
    private final Map<Long, Integer> pendingPlannedAttemptsByVm = new LinkedHashMap<>();

    /**
     * Quantidade de tentativas de migração atualmente ativas por VM.
     */
    private final Map<Long, Integer> activeAttemptsByVm = new LinkedHashMap<>();

    /**
     * Registra o mapa de migrações produzido pela política de alocação.
     * Cada entrada VM -> Host corresponde a uma migração planejada.
     */
    public void recordPlanned(final Map<Vm, Host> migrationMap) {
        if (migrationMap == null || migrationMap.isEmpty()) {
            return;
        }

        migrationMap.forEach((vm, targetHost) -> {
            planned++;
            pendingPlannedAttemptsByVm.merge(
                    vm.getId(),
                    1,
                    Integer::sum
            );
        });
    }

    /**
     * Registra o início real de uma migração.
     */
    public void recordStart(final VmHostEventInfo info) {
        started++;

        final long vmId = info.getVm().getId();
        final Integer plannedAttempts = pendingPlannedAttemptsByVm.get(vmId);

        if (plannedAttempts == null || plannedAttempts <= 0) {
            System.err.printf(
                    "Warning: migration start received for VM %d without a registered plan.%n",
                    vmId
            );
        } else {
            pendingPlannedAttemptsByVm.computeIfPresent(
                    vmId,
                    (id, count) -> count > 1 ? count - 1 : null
            );
        }

        activeAttemptsByVm.merge(
                vmId,
                1,
                Integer::sum
        );
    }

    /**
     * Registra o resultado de uma migração previamente iniciada.
     */
    public void recordResult(final DatacenterVmMigrationEventInfo info) {
        final long vmId = info.getVm().getId();
        final Integer attempts = activeAttemptsByVm.get(vmId);

        if (attempts == null || attempts <= 0) {
            System.err.printf(
                    "Warning: migration result received for VM %d without a registered start.%n",
                    vmId
            );
            return;
        }

        if (info.isMigrationSuccessful()) {
            successful++;
        } else {
            failed++;
        }

        activeAttemptsByVm.computeIfPresent(
                vmId,
                (id, count) -> count > 1 ? count - 1 : null
        );
    }

    public int getPlanned() {
        return planned;
    }

    public int getStarted() {
        return started;
    }

    public int getCompleted() {
        return successful + failed;
    }

    public int getSuccessful() {
        return successful;
    }

    public int getFailed() {
        return failed;
    }

    /**
     * Migrações planejadas pela política que ainda não chegaram ao evento real
     * de início.
     */
    public int getNotStarted() {
        return pendingPlannedAttemptsByVm.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    /**
     * Migrações que começaram, mas ainda não receberam resultado final.
     */
    public int getUnfinished() {
        return activeAttemptsByVm.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public String getNotStartedVmIds() {
        return pendingPlannedAttemptsByVm.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    public String getUnfinishedVmIds() {
        return activeAttemptsByVm.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    /**
     * Verifica as duas relações do ciclo de vida:
     *
     * planned = started + notStarted
     * started = completed + unfinished
     */
    public boolean isConsistent() {
        return planned == started + getNotStarted()
                && started == getCompleted() + getUnfinished();
    }

    /**
     * Exibe um resumo das migrações registradas durante a simulação.
     */
    public void printSummary() {
        System.out.printf("Planned VM migrations: %d%n", planned);
        System.out.printf("Started VM migrations: %d%n", started);
        System.out.printf("Completed VM migrations: %d%n", getCompleted());
        System.out.printf("Successful VM migrations: %d%n", successful);
        System.out.printf("Failed VM migrations: %d%n", failed);
        System.out.printf("Planned but not started VM migrations: %d%n", getNotStarted());
        System.out.printf("Unfinished VM migrations: %d%n", getUnfinished());

        if (!pendingPlannedAttemptsByVm.isEmpty()) {
            System.out.printf(
                    "VMs planned but not started: %s%n",
                    getNotStartedVmIds()
            );
        }

        if (!activeAttemptsByVm.isEmpty()) {
            System.out.printf(
                    "VMs with unfinished migration attempts: %s%n",
                    getUnfinishedVmIds()
            );
        }

        if (!isConsistent()) {
            System.err.println(
                    "Warning: inconsistent migration counters."
            );
        }
    }
}
