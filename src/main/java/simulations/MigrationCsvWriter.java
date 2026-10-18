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
import java.util.Map;

/**
 * Exporta para CSV o resumo produzido pelo {@link MigrationTracker}.
 *
 * Cada estratégia ocupa uma única linha do arquivo.
 * Se a estratégia já existir, sua linha é atualizada.
 * Caso contrário, uma nova linha é adicionada.
 */
public final class MigrationCsvWriter {

    private static final Path RESULTS_DIR =
            Path.of("migrations_results");

    private static final Path OUTPUT_FILE =
            RESULTS_DIR.resolve("migration_counts.csv");

    private static final String HEADER =
            "Strategy,StartedMigrations,CompletedMigrations,"
            + "SuccessfulMigrations,FailedMigrations,UnfinishedMigrations";

    private MigrationCsvWriter() {
        // Classe utilitária: não deve ser instanciada.
    }

    /**
     * Cria ou atualiza a linha correspondente à estratégia informada.
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

        if (!tracker.isConsistent()) {
            throw new IllegalStateException(
                    "Cannot export inconsistent migration counters "
                    + "for strategy: " + strategy
            );
        }

        Files.createDirectories(RESULTS_DIR);

        final Map<String, String> rowsByStrategy =
                readExistingRows();

        rowsByStrategy.put(
                strategy,
                createRow(strategy, tracker)
        );

        final List<String> lines = new ArrayList<>();

        lines.add(HEADER);
        lines.addAll(rowsByStrategy.values());

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
            /*
             * Alguns sistemas de arquivos não suportam movimento atômico.
             * Nesse caso, faz a substituição normal.
             */
            Files.move(
                    temporaryFile,
                    OUTPUT_FILE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    /**
     * Lê as estratégias já existentes no CSV.
     *
     * Também aceita o formato anterior:
     *
     * Strategy,Migrations,SuccessfulMigrations,
     * FailedMigrations,UnfinishedMigrations
     *
     * Nesse caso:
     *
     * StartedMigrations = Migrations
     * CompletedMigrations = SuccessfulMigrations + FailedMigrations
     */
    private static Map<String, String> readExistingRows()
            throws IOException {

        final Map<String, String> rowsByStrategy =
                new LinkedHashMap<>();

        if (!Files.exists(OUTPUT_FILE)) {
            return rowsByStrategy;
        }

        final List<String> lines =
                Files.readAllLines(
                        OUTPUT_FILE,
                        StandardCharsets.UTF_8
                );

        for (int i = 1; i < lines.size(); i++) {

            final String line = lines.get(i).trim();

            if (line.isEmpty()) {
                continue;
            }

            final String[] columns =
                    line.split(",", -1);

            /*
             * Formato atual:
             *
             * Strategy
             * Started
             * Completed
             * Successful
             * Failed
             * Unfinished
             */
            if (columns.length == 6
                    && !columns[0].isBlank()) {

                rowsByStrategy.put(
                        columns[0],
                        line
                );

                continue;
            }

            /*
             * Formato anterior:
             *
             * Strategy
             * Migrations
             * Successful
             * Failed
             * Unfinished
             */
            if (columns.length == 5
                    && !columns[0].isBlank()) {

                try {
                    final int started =
                            Integer.parseInt(columns[1]);

                    final int successful =
                            Integer.parseInt(columns[2]);

                    final int failed =
                            Integer.parseInt(columns[3]);

                    final int unfinished =
                            Integer.parseInt(columns[4]);

                    final int completed =
                            successful + failed;

                    final String convertedRow =
                            String.format(
                                    "%s,%d,%d,%d,%d,%d",
                                    columns[0],
                                    started,
                                    completed,
                                    successful,
                                    failed,
                                    unfinished
                            );

                    rowsByStrategy.put(
                            columns[0],
                            convertedRow
                    );

                } catch (final NumberFormatException e) {
                    System.err.printf(
                            "Warning: invalid migration CSV row ignored: %s%n",
                            line
                    );
                }
            }
        }

        return rowsByStrategy;
    }

    /**
     * Cria a linha CSV de uma estratégia.
     */
    private static String createRow(
            final String strategy,
            final MigrationTracker tracker) {

        return String.format(
                "%s,%d,%d,%d,%d,%d",
                strategy,
                tracker.getStarted(),
                tracker.getCompleted(),
                tracker.getSuccessful(),
                tracker.getFailed(),
                tracker.getUnfinished()
        );
    }
}