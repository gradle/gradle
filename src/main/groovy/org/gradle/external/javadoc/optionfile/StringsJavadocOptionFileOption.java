package org.gradle.external.javadoc.optionfile;

import java.util.List;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class StringsJavadocOptionFileOption extends AbstractListJavadocOptionFileOption<List<String>> {
    protected StringsJavadocOptionFileOption(String option, String joinBy) {
        super(option, joinBy);
    }

    protected StringsJavadocOptionFileOption(String option, List<String> value, String joinBy) {
        super(option, value, joinBy);
    }

    public void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException {
        writerContext.writeValuesOption(option, value, joinBy);
    }
}
