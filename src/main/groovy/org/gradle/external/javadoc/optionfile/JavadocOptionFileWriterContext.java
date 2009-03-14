package org.gradle.external.javadoc.optionfile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Tom Eyckmans
 */
public class JavadocOptionFileWriterContext {
    private final BufferedWriter writer;

    public JavadocOptionFileWriterContext(BufferedWriter writer) {
        this.writer = writer;
    }

    public void write(String string) throws IOException {
        writer.write(string);
    }

    public void newLine() throws IOException {
        writer.newLine();
    }

    public void writeOptionHeader(String option) throws IOException {
        write("-");
        write(option);
        write(" ");
    }

    public void writeOption(String option) throws IOException {
        writeOptionHeader(option);
        newLine();
    }

    public void writeValueOption(String option, String value) throws IOException {
        writeOptionHeader(option);
        write(value);
        newLine();
    }

    public void writeValueOption(String option, Collection<String> values) throws IOException {
        for ( final String value : values ) {
            writeValueOption(option, value);
        }
    }

    public void writeValuesOption(String option, Collection<String> values, String joinValuesBy) throws IOException {
        writeOptionHeader(option);
        final Iterator<String> valuesIt = values.iterator();
        while ( valuesIt.hasNext() ) {
            write(valuesIt.next());
            if ( valuesIt.hasNext() )
                write(joinValuesBy);
        }
        newLine();
    }

    public void writePathOption(String option, Collection<File> files, String joinValuesBy) throws IOException {
        writeOptionHeader(option);
        final Iterator<File> filesIt = files.iterator();
        while ( filesIt.hasNext() ) {
            write(filesIt.next().getAbsolutePath());
            if ( filesIt.hasNext() )
                write(joinValuesBy);
        }
        newLine();
    }

}
