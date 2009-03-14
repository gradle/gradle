package org.gradle.external.javadoc.optionfile;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class StringJavadocOptionFileOption extends AbstractJavadocOptionFileOption<String> {
    public StringJavadocOptionFileOption(String option) {
        super(option);
    }

    public StringJavadocOptionFileOption(String option, String value) {
        super(option, value);
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null ) 
            writerContext.writeValueOption(option, value);
    }
}
