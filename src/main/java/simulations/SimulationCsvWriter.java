package simulations;

import org.cloudsimplus.brokers.DatacenterBroker;

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
 * Exporta contagens básicas de Cloudlets para validação da completude
 * de cada estratégia executada.
 *
 * O arquivo gerado permite comparar quantas Cloudlets foram submetidas
 * ao Broker com quantas efetivamente chegaram à lista de finalizadas.
 */
public final class SimulationCsvWriter {

    private static final Path RESULTS_DIR =
            Path.of("migrations_results");

    private static final Path OUTPUT_FILE =
            RESULTS_DIR.resolve("simulation_counts.csv");

    private static final String HEADER =
            "Strategy,SubmittedCloudlets,FinishedCloudlets";

    private SimulationCsvWriter() {
        // Classe utilitária: não deve ser instanciada.
    }

    /**
     * Cria ou atualiza a linha correspondente à estratégia informada.
     */
    public static void writeOrUpdate(
            final String strategy,
            final DatacenterBroker broker) throws IOException {

        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException(
                    "Strategy name cannot be null or blank."
            );
        }

        if (broker == null) {
            throw new IllegalArgumentException(
                    "DatacenterBroker cannot be null."
            );
        }

        Files.createDirectories(RESULTS_DIR);

        final Map<String, String> rowsByStrategy =
                readExistingRows();

        rowsByStrategy.put(
                strategy,
                createRow(strategy, broker)
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
            Files.move(
                    temporaryFile,
                    OUTPUT_FILE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    /**
     * Lê as linhas existentes para preservar os resultados das demais
     * estratégias e evitar duplicação quando uma estratégia é executada
     * novamente.
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

            final String[] columns = line.split(",", -1);

            if (columns.length == 3 && !columns[0].isBlank()) {
                rowsByStrategy.put(columns[0], line);
            } else {
                System.err.printf(
                        "Warning: invalid simulation CSV row ignored: %s%n",
                        line
                );
            }
        }

        return rowsByStrategy;
    }

    /**
     * Gera a linha com as contagens reais mantidas pelo Broker ao final
     * da execução da estratégia.
     */
    private static String createRow(
            final String strategy,
            final DatacenterBroker broker) {

        return String.format(
                "%s,%d,%d",
                strategy,
                broker.getCloudletSubmittedList().size(),
                broker.getCloudletFinishedList().size()
        );
    }
}
