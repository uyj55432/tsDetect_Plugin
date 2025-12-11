package es.upm.grupoab.profundizacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class TsDetect_manager {

    private static Path extractTsDetectJar(Path workDir) throws IOException {
        String resourcePath = "/TestSmellDetector.jar";

        Files.createDirectories(workDir);

        Path jarPath = workDir.resolve("TestSmellDetector.jar");

        try (InputStream is = TsDetect_manager.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("No se encontró el recurso: " + resourcePath);
            }
            Files.copy(is, jarPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return jarPath;
    }

    public static Path runTsDetect(Path csvInputPath, Path projectRoot)
            throws IOException, InterruptedException {

        Path workDir = projectRoot.resolve("target/tsdetect");
        Files.createDirectories(workDir);

        // 1. Extraer el jar en target/tsdetect
        Path tsDetectJar = extractTsDetectJar(workDir);

        // 2. Ejecutar el jar
        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-jar",
                tsDetectJar.toAbsolutePath().toString(),
                csvInputPath.toAbsolutePath().toString());

        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 3. Leer LOGS
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[tsDetect] " + line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("tsDetect terminó con error. Exit code=" + exitCode);
        }

        // 4. Localizar el CSV generado por tsDetect
        try (Stream<Path> files = Files.list(workDir)) {
            return files
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("tsDetect no generó ningún CSV en " + workDir));
        }
    }

    public static void readTsDetectResults(Path resultCsv) throws IOException {
        Files.lines(resultCsv)
                .skip(1) // si tiene cabecera
                .forEach(System.out::println);
    }

}