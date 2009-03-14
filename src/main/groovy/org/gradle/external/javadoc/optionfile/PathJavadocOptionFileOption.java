package org.gradle.external.javadoc.optionfile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class PathJavadocOptionFileOption extends AbstractListJavadocOptionFileOption<List<File>> {

    protected PathJavadocOptionFileOption(String option, String joinBy) {
        super(option, joinBy);
    }

    protected PathJavadocOptionFileOption(String option, List<File> value, String joinBy) {
        super(option, value, joinBy);
    }

    public void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException {
        writerContext.writePathOption(option, value, joinBy);
    }
}
