package org.gradle.external.javadoc.optionfile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class JavadocOptionFile {
    private final Map<String, JavadocOptionFileOption> options;

    private final OptionLessJavadocOptionFileOption<List<String>> packageNames;
    private final OptionLessJavadocOptionFileOption<List<String>> sourceNames;

    public JavadocOptionFile() {
        options = new HashMap<String, JavadocOptionFileOption>();
        packageNames = new OptionLessStringsJavadocOptionFileOption();
        sourceNames = new OptionLessStringsJavadocOptionFileOption();
    }

    public OptionLessJavadocOptionFileOption<List<String>> getPackageNames() {
        return packageNames;
    }

    public OptionLessJavadocOptionFileOption<List<String>> getSourceNames() {
        return sourceNames;
    }

    Map<String, JavadocOptionFileOption> getOptions() {
        return Collections.unmodifiableMap(options);
    }

    public <T> JavadocOptionFileOption<T> addOption(JavadocOptionFileOption<T> option) {
        if ( option == null ) throw new IllegalArgumentException("option == null!");

        options.put(option.getOption(), option);

        return option;
    }

    public JavadocOptionFileOption<String> addStringOption(String option) {
        return addStringOption(option, null);
    }

    public JavadocOptionFileOption<String> addStringOption(String option, String value) {
        return addOption(new StringJavadocOptionFileOption(option, value));
    }

    public <T> JavadocOptionFileOption<T> addEnumOption(String option) {
        return addEnumOption(option, null);
    }

    public <T> JavadocOptionFileOption<T> addEnumOption(String option, T value) {
        return addOption(new EnumJavadocOptionFileOption<T>(option, value));
    }

    public JavadocOptionFileOption<List<File>> addPathOption(String option) {
        return addPathOption(option, System.getProperty("path.separator"));
    }

    public JavadocOptionFileOption<List<File>> addPathOption(String option, String joinBy) {
        return addOption(new PathJavadocOptionFileOption(option, joinBy));
    }

    public JavadocOptionFileOption<List<String>> addStringsOption(String option) {
        return addStringsOption(option, System.getProperty("path.separator"));
    }

    public JavadocOptionFileOption<List<String>> addStringsOption(String option, String joinBy) {
        return addOption(new StringsJavadocOptionFileOption(option, new ArrayList<String>(), joinBy));
    }

   public JavadocOptionFileOption<List<String>> addMultilineStringsOption(String option) {
       return addOption(new MultilineStringsJavadocOptionFileOption(option, new ArrayList<String>()));
   }

    public JavadocOptionFileOption<Boolean> addBooleanOption(String option) {
        return addBooleanOption(option, false);
    }

    public JavadocOptionFileOption<Boolean> addBooleanOption(String option, boolean value) {
        return addOption(new BooleanJavadocOptionFileOption(option, value));
    }

    public JavadocOptionFileOption<File> addFileOption(String option) {
        return addFileOption(option, null);
    }

    public JavadocOptionFileOption<File> addFileOption(String option, File value) {
        return addOption(new FileJavadocOptionFileOption(option, value));
    }

    public void write(File optionFile) throws IOException {
        if ( optionFile == null ) throw new IllegalArgumentException("optionFile == null!");

        final JavadocOptionFileWriter optionFileWriter = new JavadocOptionFileWriter(this);

        optionFileWriter.write(optionFile);
    }
}
