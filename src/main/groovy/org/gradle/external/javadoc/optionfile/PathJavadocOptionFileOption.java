package org.gradle.external.javadoc.optionfile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class PathJavadocOptionFileOption extends AbstractListJavadocOptionFileOption<List<File>> {

    public PathJavadocOptionFileOption(String option, String joinBy) {
        super(option, new ArrayList<File>(), joinBy);
    }

    public PathJavadocOptionFileOption(String option, List<File> value, String joinBy) {
        super(option, value, joinBy);
    }

    public void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException {
        writerContext.writePathOption(option, value, joinBy);
    }
}
