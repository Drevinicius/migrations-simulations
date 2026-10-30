package simulations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exporta o histórico detalhado das migrações planejadas e executadas.
 *
 * Diferentemente de migration_counts.csv, que contém uma linha agregada por
 * estratégia, este arquivo contém uma linha por tentativa planejada de migração.
 */
public final class MigrationMovementCsvWriter {

    private static final Path RESULTS_DIR =
            Path.of("migrations_results");

    private static final Path OUTPUT_FILE =
            RESULTS_DIR.resolve("migration_movements.csv");

    private static final String HEADER =
            "Strategy,AttemptId,VmId,PlannedSourceHost,PlannedTargetHost,"
            + "ActualSourceHost,ActualTargetHost,Started,Completed,Successful,"
            + "Status,StartTime,FinishTime";

    private MigrationMovementCsvWriter() {
        // Classe utilitária: não deve ser instanciada.
    }

    /**
     * Substitui no CSV todas as linhas da estratégia informada pelos dados da
     * execução atual, preservando as linhas das demais estratégias.
     */
    public static void writeOrUpdate(
            final String strategy,
            final MigrationTracker tracker) throws IOException {

        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException(
                    "Strategy name cannot be null or blank."
            );
        }

        if (tracker == null) {
            throw new IllegalArgumentException(
                    "MigrationTracker cannot be null."
            );
        }

        Files.createDirectories(RESULTS_DIR);

        final Map<String, List<String>> rowsByStrategy =
                readExistingRows();

        final List<String> currentRows = new ArrayList<>();

        for (final MigrationTracker.MigrationAttempt attempt : tracker.getAttempts()) {
            currentRows.add(createRow(strategy, attempt));
        }

        rowsByStrategy.put(strategy, currentRows);

        final List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        rowsByStrategy.values().forEach(lines::addAll);

        final Path temporaryFile =
                OUTPUT_FILE.resolveSibling(
                        OUTPUT_FILE.getFileName() + ".tmp"
                );

        Files.write(
                temporaryFile,
                lines,
                StandardCharsets.UTF_8
        );

        try {
            Files.move(
                    temporaryFile,
                    OUTPUT_FILE,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(
                    temporaryFile,
                    OUTPUT_FILE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static Map<String, List<String>> readExistingRows()
            throws IOException {

        final Map<String, List<String>> rowsByStrategy =
                new LinkedHashMap<>();

        if (!Files.exists(OUTPUT_FILE)) {
            return rowsByStrategy;
        }

        final List<String> lines = Files.readAllLines(
                OUTPUT_FILE,
                StandardCharsets.UTF_8
        );

        for (int i = 1; i < lines.size(); i++) {
            final String line = lines.get(i).trim();

            if (line.isEmpty()) {
                continue;
            }

            final String[] columns = line.split(",", -1);

            if (columns.length != 13 || columns[0].isBlank()) {
                System.err.printf(
                        "Warning: invalid migration movement CSV row ignored: %s%n",
                        line
                );
                continue;
            }

            rowsByStrategy
                    .computeIfAbsent(columns[0], ignored -> new ArrayList<>())
                    .add(line);
        }

        return rowsByStrategy;
    }

    private static String createRow(
            final String strategy,
            final MigrationTracker.MigrationAttempt attempt) {

        return String.join(",",
                strategy,
                String.valueOf(attempt.getAttemptId()),
                String.valueOf(attempt.getVmId()),
                hostId(attempt.getPlannedSourceHostId()),
                hostId(attempt.getPlannedTargetHostId()),
                hostId(attempt.getActualSourceHostId()),
                hostId(attempt.getActualTargetHostId()),
                String.valueOf(attempt.isStarted()),
                String.valueOf(attempt.isCompleted()),
                nullableBoolean(attempt.getSuccessful()),
                attempt.getStatus(),
                nullableDouble(attempt.getStartTime()),
                nullableDouble(attempt.getFinishTime())
        );
    }

    private static String hostId(final long hostId) {
        return hostId < 0 ? "" : String.valueOf(hostId);
    }

    private static String nullableBoolean(final Boolean value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullableDouble(final Double value) {
        return value == null
                ? ""
                : String.format(Locale.US, "%.2f", value);
    }
}
