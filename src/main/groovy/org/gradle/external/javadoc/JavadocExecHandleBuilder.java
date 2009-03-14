package org.gradle.external.javadoc;

import java.io.File;
import java.io.IOException;

import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;
import org.gradle.api.GradleException;
import org.apache.tools.ant.util.JavaEnvUtils;

/**
 * @author Tom Eyckmans
 */
public class JavadocExecHandleBuilder {
    private File execDirectory;
    private MinimalJavadocOptions options;
    private String optionsFilename;
    private File destinationDirectory;

    public JavadocExecHandleBuilder() {
    }

    public JavadocExecHandleBuilder execDirectory(File directory) {
        this.execDirectory = directory;
        return this;
    }

    public JavadocExecHandleBuilder options(MinimalJavadocOptions options) {
        this.options = options;
        return this;
    }

    public JavadocExecHandleBuilder optionsFilename(String optionsFilename) {
        this.optionsFilename = optionsFilename;
        return this;
    }

    public JavadocExecHandleBuilder destinationDirectory(File destinationDirectory) {
        this.destinationDirectory = destinationDirectory;
        return this;
    }

    public ExecHandle getExecHandle() {
        final File javadocOptionsFile = new File(destinationDirectory, optionsFilename);

        try {
            options.write(javadocOptionsFile);
        }
        catch ( IOException e ) {
            throw new GradleException("Faild to store javadoc options.", e);
        }

        final ExecHandleBuilder execHandleBuilder = new ExecHandleBuilder(true)
            .execDirectory(execDirectory)
            .execCommand(JavaEnvUtils.getJdkExecutable("javadoc")) // reusing Ant knowledge here would be stupid not to
            .arguments("@"+javadocOptionsFile.getAbsolutePath());

        options.contributeCommandLineOptions(execHandleBuilder);

        return execHandleBuilder.getExecHandle();
    }
}
