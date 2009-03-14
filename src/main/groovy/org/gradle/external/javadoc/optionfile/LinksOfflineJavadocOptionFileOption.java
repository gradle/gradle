package org.gradle.external.javadoc.optionfile;

import org.gradle.external.javadoc.JavadocOfflineLink;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class LinksOfflineJavadocOptionFileOption extends AbstractJavadocOptionFileOption<List<JavadocOfflineLink>> {
    public LinksOfflineJavadocOptionFileOption(String option) {
        super(option, new ArrayList<JavadocOfflineLink>());
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null && !value.isEmpty() ) {
            for ( final JavadocOfflineLink offlineLink : value ) {
                writerContext.writeValueOption(option, offlineLink.toString());
            }
        }
    }
}
