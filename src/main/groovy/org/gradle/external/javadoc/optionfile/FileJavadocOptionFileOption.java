package org.gradle.external.javadoc.optionfile;

import java.io.File;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class FileJavadocOptionFileOption extends AbstractJavadocOptionFileOption<File> {
    protected FileJavadocOptionFileOption(String option) {
        super(option);
    }

    protected FileJavadocOptionFileOption(String option, File value) {
        super(option, value);
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null ) {
            writerContext.writeValueOption(option, value.getAbsolutePath());
        }
    }
}
