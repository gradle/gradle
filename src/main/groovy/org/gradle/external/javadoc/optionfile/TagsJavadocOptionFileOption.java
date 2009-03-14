package org.gradle.external.javadoc.optionfile;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class TagsJavadocOptionFileOption extends AbstractJavadocOptionFileOption<List<String>> {
    protected TagsJavadocOptionFileOption(String option) {
        super(option, new ArrayList<String>());
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
            for ( final String tag : value ) {
                if ( tag.contains(":") || tag.contains("\"") ) {
                    writerContext.writeValueOption("tag", tag);
                }
                else {
                    writerContext.writeValueOption("taglet", tag);
                }
            }
        }
    }
}
