package org.gradle.external.javadoc.optionfile;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class JavadocOptionFileWriter {
    private final JavadocOptionFile optionFile;

    public JavadocOptionFileWriter(JavadocOptionFile optionFile) {
        if ( optionFile == null ) throw new IllegalArgumentException("optionFile == null!");
        this.optionFile = optionFile;
    }

    void write(File outputFile) throws IOException {
        BufferedWriter writer = null;
        try {
            final Map<String, JavadocOptionFileOption> options = optionFile.getOptions();
            writer = new BufferedWriter(new FileWriter(outputFile));
            JavadocOptionFileWriterContext writerContext = new JavadocOptionFileWriterContext(writer);

            for ( final String option : options.keySet() ) {
                options.get(option).write(writerContext);
            }

            optionFile.getPackageNames().write(writerContext);
            optionFile.getSourceNames().write(writerContext);
        }
        finally {
            IOUtils.closeQuietly(writer);
        }
    }
}
