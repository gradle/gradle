package org.gradle.external.javadoc.optionfile;

import java.util.List;
import java.util.Collections;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractListJavadocOptionFileOption<T extends List> extends AbstractJavadocOptionFileOption<T> {
    protected String joinBy;

    protected AbstractListJavadocOptionFileOption(String option, String joinBy) {
        super(option);
        this.joinBy = joinBy;
    }

    protected AbstractListJavadocOptionFileOption(String option, T value, String joinBy) {
        super(option, value);
        this.joinBy = joinBy;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        if ( value == null ) {
            this.value.clear();
        }
        else {
            this.value = value;
        }
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null && !value.isEmpty() ) {
            writeCollectionValue(writerContext);
        }
    }

    protected abstract void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException;
}
