package org.gradle.external.javadoc.optionfile;

import org.gradle.util.GUtil;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class GroupsJavadocOptionFileOption extends AbstractJavadocOptionFileOption<Map<String, List<String>>> {
    public GroupsJavadocOptionFileOption(String option) {
        super(option, new HashMap<String, List<String>>());
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null && !value.isEmpty() ) {
            for ( final String group : value.keySet() ) {
                final List<String> groupPackages = value.get(group);

                writerContext.writeOptionHeader(option);
                writerContext.write("\"");
                writerContext.write(group);
                writerContext.write("\"");
                writerContext.write(" ");
                writerContext.write("\"");
                writerContext.write(GUtil.join(groupPackages, ":"));
                writerContext.write("\"");
                writerContext.newLine();
            }
        }
    }
}
