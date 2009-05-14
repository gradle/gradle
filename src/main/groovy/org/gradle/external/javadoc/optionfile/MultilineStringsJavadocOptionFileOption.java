package org.gradle.external.javadoc.optionfile;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

/**
 * @author Melanie Pfautz
 */
public class MultilineStringsJavadocOptionFileOption extends AbstractListJavadocOptionFileOption<List<String>> {

   // We should never attempt to join strings so if you see this, there's a problem
    private static final String JOIN_BY = "Not Used!";

    protected MultilineStringsJavadocOptionFileOption(String option) {
        super(option, new ArrayList<String>(), JOIN_BY);
    }

    protected MultilineStringsJavadocOptionFileOption(String option, List<String> value) {
        super(option, value, JOIN_BY);
    }

    public void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null && !value.isEmpty() ) {
            writerContext.writeMultilineValuesOption(option, value);
       }
    }
}
