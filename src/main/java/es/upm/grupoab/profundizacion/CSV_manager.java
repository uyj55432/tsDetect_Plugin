package es.upm.grupoab.profundizacion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CSV_manager {

    public static void generateCsv(Path projectRoot, Path outputCsv) throws IOException {
        String projectName = projectRoot.getFileName().toString();
        Path testRoot = projectRoot.resolve("src/test/java");
        Path mainRoot = projectRoot.resolve("src/main/java");

        if (!Files.exists(testRoot)) {
            System.err.println("No existe el directorio de tests: " + testRoot);
            return;
        }

        // Creamos carpeta de salida si no existe
        if (outputCsv.getParent() != null) {
            Files.createDirectories(outputCsv.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputCsv, StandardCharsets.UTF_8)) {

            // Recorremos todos los ficheros bajo src/test/java
            try (Stream<Path> stream = Files.walk(testRoot)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> p.getFileName().toString().endsWith("Test.java"))
                        .forEach(testFile -> {
                            try {
                                String testPathStr = testFile.toAbsolutePath().toString();
                                String mainPathStr = findMainClassForTest(testFile, testRoot, mainRoot);

                                // Línea CSV: projectName,testPath,mainPath
                                String line = csvEscape(projectName) + "," +
                                        csvEscape(testPathStr) + "," +
                                        csvEscape(mainPathStr);

                                writer.write(line);
                                writer.newLine();

                            } catch (IOException e) {
                                // Puedes manejarlo mejor según tus necesidades
                                throw new RuntimeException("Error escribiendo CSV", e);
                            }
                        });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw e;
            }
        }

        System.out.println("CSV generado en: " + outputCsv.toAbsolutePath());
    }

    private static String findMainClassForTest(Path testFile, Path testRoot, Path mainRoot) {
        Path relative = testRoot.relativize(testFile); // com/ejemplo/GraphTest.java
        Path parent = relative.getParent(); // com/ejemplo
        String fileName = relative.getFileName().toString(); // GraphTest.java

        // Si no termina en Test.java, devolvemos vacío
        if (!fileName.endsWith("Test.java")) {
            return "";
        }

        String baseName = fileName.substring(0, fileName.length() - "Test.java".length());
        String mainFileName = baseName + ".java";

        Path mainRelative;
        if (parent != null) {
            mainRelative = parent.resolve(mainFileName); // com/ejemplo/Graph.java
        } else {
            mainRelative = Paths.get(mainFileName);
        }

        Path mainFile = mainRoot.resolve(mainRelative);

        if (Files.exists(mainFile)) {
            return mainFile.toAbsolutePath().toString();
        } else {
            // No se encontró clase correspondiente
            return "";
        }
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean mustQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!mustQuote) {
            return value;
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public static void prettyPrintTsDetectResults(Path resultCsv) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(resultCsv, java.nio.charset.Charset.defaultCharset())) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                System.out.println("El CSV de tsDetect esta vacio.");
                return;
            }

            String[] headers = headerLine.split(",", -1);

            // Índices fijos según el formato
            final int IDX_TESTCLASS = 1;
            final int IDX_NUM_METHODS = 6;
            final int SMELL_START_IDX = 7;

            System.out.println("\n========== RESULTADOS TSDETECT ==========\n");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                String[] values = line.split(",", -1);
                if (values.length <= IDX_NUM_METHODS) {
                    continue;
                }

                String testClass = safeGet(values, IDX_TESTCLASS);
                String numMethodsStr = safeGet(values, IDX_NUM_METHODS);
                int numMethods = parseIntSafe(numMethodsStr);

                System.out.println("Test     : " + testClass);
                System.out.println("Metodos  : " + numMethods);

                List<String> smells = new ArrayList<>();
                for (int i = SMELL_START_IDX; i < headers.length && i < values.length; i++) {
                    String smellName = headers[i].trim();
                    int count = parseIntSafe(values[i]);
                    if (count > 0) {
                        smells.add(" - " + smellName + ": " + count);
                    }
                }

                if (smells.isEmpty()) {
                    System.out.println("Smells   : Sin test smells detectados");
                } else {
                    System.out.println("Smells   :");
                    smells.forEach(System.out::println);
                }

                System.out.println("\n----------------------------------------\n");
            }

            System.out.println("========================================\n");
        }
    }

    public static void printTsDetectSummary(Path resultCsv) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(resultCsv, java.nio.charset.Charset.defaultCharset())) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                System.out.println("El CSV de tsDetect esta vacio.");
                return;
            }

            String[] headers = headerLine.split(",", -1);

            final int IDX_NUM_METHODS = 6;
            final int SMELL_START_IDX = 7;

            int[] smellTotals = new int[headers.length];
            int testCount = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                String[] values = line.split(",", -1);
                if (values.length <= IDX_NUM_METHODS)
                    continue;

                testCount++;

                for (int i = SMELL_START_IDX; i < headers.length && i < values.length; i++) {
                    smellTotals[i] += parseIntSafe(values[i]);
                }
            }

            System.out.println("\n====== RESUMEN GLOBAL TSDETECT ======\n");
            System.out.println("Numero de tests analizados: " + testCount + "\n");
            System.out.println("Total de smells por tipo:\n");

            boolean anySmell = false;
            for (int i = SMELL_START_IDX; i < headers.length; i++) {
                int total = smellTotals[i];
                if (total > 0) {
                    anySmell = true;
                    System.out.printf(" - %-30s : %d%n", headers[i].trim(), total);
                }
            }

            if (!anySmell) {
                System.out.println("Sin test smells detectados en ningun test.");
            }

            System.out.println("\n=====================================\n");
        }
    }

    private static String safeGet(String[] arr, int idx) {
        if (idx < 0 || idx >= arr.length)
            return "";
        return arr[idx] == null ? "" : arr[idx];
    }

    private static int parseIntSafe(String s) {
        try {
            if (s == null || s.isEmpty())
                return 0;
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}