package org.gradle.external.javadoc.optionfile;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public interface OptionLessJavadocOptionFileOption<T> {
    T getValue();

    void setValue(T value);

    void write(JavadocOptionFileWriterContext writerContext) throws IOException;
}
