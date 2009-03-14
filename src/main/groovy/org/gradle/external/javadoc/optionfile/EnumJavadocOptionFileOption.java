package org.gradle.external.javadoc.optionfile;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class EnumJavadocOptionFileOption<T> extends AbstractJavadocOptionFileOption<T> {
    public EnumJavadocOptionFileOption(String option) {
        super(option);
    }

    public EnumJavadocOptionFileOption(String option, T value) {
        super(option, value);
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null ) {
            writerContext.writeOption(value.toString().toLowerCase());
        }
    }
}
