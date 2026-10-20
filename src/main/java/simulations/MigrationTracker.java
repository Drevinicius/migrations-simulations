package simulations;

import org.cloudsimplus.listeners.DatacenterVmMigrationEventInfo;
import org.cloudsimplus.listeners.VmHostEventInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rastreia migrações de VMs iniciadas durante a simulação.
 *
 * Cada migração iniciada é registrada pelo evento da VM e permanece ativa
 * até que o Datacenter informe seu resultado.
 *
 * As migrações podem ser classificadas como:
 * - iniciadas;
 * - concluídas;
 * - concluídas com sucesso;
 * - concluídas com falha;
 * - ainda não finalizadas.
 */
public final class MigrationTracker {

    private int started;
    private int successful;
    private int failed;

    /**
     * Mantém a quantidade de tentativas de migração atualmente ativas por VM.
     * O contador permite que uma mesma VM seja migrada mais de uma vez
     * durante a mesma simulação.
     */
    private final Map<Long, Integer> activeAttemptsByVm = new LinkedHashMap<>();

    /**
     * Registra o início de uma migração.
     */
    public void recordStart(final VmHostEventInfo info) {
        started++;

        activeAttemptsByVm.merge(
                info.getVm().getId(),
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

    public int getUnfinished() {
        return activeAttemptsByVm.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public String getUnfinishedVmIds() {
        return activeAttemptsByVm.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    /**
     * Verifica se todas as migrações iniciadas estão contabilizadas
     * como concluídas ou ainda pendentes.
     */
    public boolean isConsistent() {
        return started == getCompleted() + getUnfinished();
    }

    /**
     * Exibe um resumo das migrações registradas durante a simulação.
     */
    public void printSummary() {
        System.out.printf("Started VM migrations: %d%n", started);
        System.out.printf("Completed VM migrations: %d%n", getCompleted());
        System.out.printf("Successful VM migrations: %d%n", successful);
        System.out.printf("Failed VM migrations: %d%n", failed);
        System.out.printf("Unfinished VM migrations: %d%n", getUnfinished());

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