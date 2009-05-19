package org.gradle.external.javadoc.optionfile;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class BooleanJavadocOptionFileOption extends AbstractJavadocOptionFileOption<Boolean> {
    protected BooleanJavadocOptionFileOption(String option) {
        super(option);
    }

    protected BooleanJavadocOptionFileOption(String option, Boolean value) {
        super(option, value);
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null && value) {
            writerContext.writeOption(option);
        }
    }
}
