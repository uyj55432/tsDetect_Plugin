package es.upm.grupoab.profundizacion;

import java.io.File;
import java.nio.file.Path;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "analyze-tsdetect")
public class MyMojo extends AbstractMojo{

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File projectRootFile;
    private Path csvResult;

    public void execute() throws MojoExecutionException{

        Path projectRoot = projectRootFile.toPath();
        Path outputCsv = projectRoot.resolve("target/tsdetect-input.csv");

        try {
            CSV_manager.generateCsv(projectRoot, outputCsv);
            
            csvResult =  TsDetect_manager.runTsDetect(outputCsv, projectRoot);
        
            CSV_manager.prettyPrintTsDetectResults(csvResult);

            CSV_manager.printTsDetectSummary(csvResult);

        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage());
        }

    }
}