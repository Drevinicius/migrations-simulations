package simulations;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.listeners.DatacenterVmMigrationEventInfo;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rastreia o ciclo completo das migrações de VMs durante a simulação.
 *
 * Além dos contadores agregados, mantém um histórico por tentativa para
 * permitir exportar as rotas planejadas e o que realmente aconteceu.
 */
public final class MigrationTracker {

    private int planned;
    private int started;
    private int successful;
    private int failed;
    private long nextAttemptId = 1;

    /**
     * Quantidade de planejamentos ainda não convertidos em início real por VM.
     */
    private final Map<Long, Integer> pendingPlannedAttemptsByVm = new LinkedHashMap<>();

    /**
     * Quantidade de tentativas de migração atualmente ativas por VM.
     */
    private final Map<Long, Integer> activeAttemptsByVm = new LinkedHashMap<>();

    /**
     * Histórico detalhado das tentativas planejadas.
     */
    private final List<MigrationAttempt> attempts = new ArrayList<>();

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

            final long sourceHostId =
                    vm.getHost() == null ? -1 : vm.getHost().getId();

            attempts.add(new MigrationAttempt(
                    nextAttemptId++,
                    vm.getId(),
                    sourceHostId,
                    targetHost.getId()
            ));

            pendingPlannedAttemptsByVm.merge(
                    vm.getId(),
                    1,
                    Integer::sum
            );
        });
    }

    /**
     * Registra o início real de uma migração e associa o evento ao planejamento
     * correspondente da mesma VM e, preferencialmente, do mesmo Host destino.
     */
    public void recordStart(final VmHostEventInfo info) {
        started++;

        final Vm vm = info.getVm();
        final long vmId = vm.getId();
        final long actualTargetHostId = info.getHost().getId();
        final long actualSourceHostId =
                vm.getHost() == null ? -1 : vm.getHost().getId();

        final MigrationAttempt attempt = findPendingAttempt(
                vmId,
                actualTargetHostId
        );

        if (attempt == null) {
            System.err.printf(
                    "Warning: migration start received for VM %d without a registered plan.%n",
                    vmId
            );
        } else {
            attempt.markStarted(
                    info.getTime(),
                    actualSourceHostId,
                    actualTargetHostId
            );

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
        final Integer activeCount = activeAttemptsByVm.get(vmId);

        if (activeCount == null || activeCount <= 0) {
            System.err.printf(
                    "Warning: migration result received for VM %d without a registered start.%n",
                    vmId
            );
            return;
        }

        final MigrationAttempt attempt = findActiveAttempt(vmId);

        if (attempt == null) {
            System.err.printf(
                    "Warning: migration result received for VM %d but no active detailed attempt was found.%n",
                    vmId
            );
        } else {
            attempt.markCompleted(
                    info.getTime(),
                    info.isMigrationSuccessful()
            );
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

    private MigrationAttempt findPendingAttempt(
            final long vmId,
            final long actualTargetHostId) {

        MigrationAttempt fallback = null;

        for (final MigrationAttempt attempt : attempts) {
            if (attempt.getVmId() != vmId || attempt.isStarted()) {
                continue;
            }

            if (attempt.getPlannedTargetHostId() == actualTargetHostId) {
                return attempt;
            }

            if (fallback == null) {
                fallback = attempt;
            }
        }

        return fallback;
    }

    private MigrationAttempt findActiveAttempt(final long vmId) {
        for (final MigrationAttempt attempt : attempts) {
            if (attempt.getVmId() == vmId
                    && attempt.isStarted()
                    && !attempt.isCompleted()) {
                return attempt;
            }
        }

        return null;
    }

    public List<MigrationAttempt> getAttempts() {
        return Collections.unmodifiableList(attempts);
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

    public int getNotStarted() {
        return pendingPlannedAttemptsByVm.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

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

    public boolean isConsistent() {
        return planned == started + getNotStarted()
                && started == getCompleted() + getUnfinished();
    }

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
            System.err.println("Warning: inconsistent migration counters.");
        }
    }

    /**
     * Registro detalhado de uma tentativa planejada de migração.
     */
    public static final class MigrationAttempt {
        private final long attemptId;
        private final long vmId;
        private final long plannedSourceHostId;
        private final long plannedTargetHostId;

        private boolean started;
        private boolean completed;
        private Boolean successful;

        private long actualSourceHostId = -1;
        private long actualTargetHostId = -1;

        private Double startTime;
        private Double finishTime;

        private MigrationAttempt(
                final long attemptId,
                final long vmId,
                final long plannedSourceHostId,
                final long plannedTargetHostId) {
            this.attemptId = attemptId;
            this.vmId = vmId;
            this.plannedSourceHostId = plannedSourceHostId;
            this.plannedTargetHostId = plannedTargetHostId;
        }

        private void markStarted(
                final double startTime,
                final long actualSourceHostId,
                final long actualTargetHostId) {
            this.started = true;
            this.startTime = startTime;
            this.actualSourceHostId = actualSourceHostId;
            this.actualTargetHostId = actualTargetHostId;
        }

        private void markCompleted(
                final double finishTime,
                final boolean successful) {
            this.completed = true;
            this.finishTime = finishTime;
            this.successful = successful;
        }

        public long getAttemptId() {
            return attemptId;
        }

        public long getVmId() {
            return vmId;
        }

        public long getPlannedSourceHostId() {
            return plannedSourceHostId;
        }

        public long getPlannedTargetHostId() {
            return plannedTargetHostId;
        }

        public boolean isStarted() {
            return started;
        }

        public boolean isCompleted() {
            return completed;
        }

        public Boolean getSuccessful() {
            return successful;
        }

        public long getActualSourceHostId() {
            return actualSourceHostId;
        }

        public long getActualTargetHostId() {
            return actualTargetHostId;
        }

        public Double getStartTime() {
            return startTime;
        }

        public Double getFinishTime() {
            return finishTime;
        }

        public String getStatus() {
            if (!started) {
                return "NOT_STARTED";
            }

            if (!completed) {
                return "UNFINISHED";
            }

            return Boolean.TRUE.equals(successful)
                    ? "SUCCESS"
                    : "FAILED";
        }
    }
}
