package org.gradle.api.tasks.javadoc;

/**
 * This enum maps to the -public, -protected, -package and -private options of the javadoc executable. 
 *
 * @author Tom Eyckmans
 */
public enum JavadocMemberLevel {
    /**
     * Shows only public classes and members.
     */
    PUBLIC,
    /**
     * Shows only protected and public classes and members. This is the default.
     */
    PROTECTED,
    /**
     * Shows only package, protected, and public classes and members.
     */
    PACKAGE,
    /**
     * Shows all classes and members. 
     */
    PRIVATE
}
