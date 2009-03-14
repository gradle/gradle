package org.gradle.api.tasks.javadoc;

/**
 * @author Tom Eyckmans
 */
public enum JavadocOutputLevel {
    /**
     * Provides more detailed messages while javadoc is running. Without the verbose option,
     * messages appear for loading the source files, generating the documentation (one message per source file), and sorting.
     * The verbose option causes the printing of additional messages specifying the number of milliseconds to parse each java source file.
     */
    VERBOSE,
    /**
     * Shuts off non-error and non-warning messages, leaving only the warnings and errors appear,
     * making them easier to view. Also suppresses the version string. 
     */
    QUIET
}
