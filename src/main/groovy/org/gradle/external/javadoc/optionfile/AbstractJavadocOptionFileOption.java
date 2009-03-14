package org.gradle.external.javadoc.optionfile;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractJavadocOptionFileOption<T> implements JavadocOptionFileOption<T> {
    protected final String option;
    protected T value;

    protected AbstractJavadocOptionFileOption(String option) {
        this(option, null);
    }

    protected AbstractJavadocOptionFileOption(String option, T value) {
        if ( option == null ) throw new IllegalArgumentException("option == null!");

        this.option = option;
        this.value = value;
    }

    public final String getOption() {
        return option;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
