package org.gradle.external.javadoc.optionfile;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class OptionLessStringsJavadocOptionFileOption implements OptionLessJavadocOptionFileOption<List<String>> {
    private List<String> value;

    public OptionLessStringsJavadocOptionFileOption() {
        value = new ArrayList<String>();
    }

    public OptionLessStringsJavadocOptionFileOption(List<String> value) {
        this.value = value;
    }

    public List<String> getValue() {
        return value;
    }

    public void setValue(List<String> value) {
        if ( value == null ) {
            this.value.clear();
        }
        else {
            this.value = value;
        }
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null && !value.isEmpty() ) {
            for ( String singleValue : value ) {
                writerContext.write(singleValue);
                writerContext.newLine();
            }
        }
    }
}
