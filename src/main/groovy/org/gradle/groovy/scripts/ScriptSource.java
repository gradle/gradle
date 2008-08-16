package org.gradle.groovy.scripts;

import java.io.File;

/**
 * The source for the text of a script.
 */
public interface ScriptSource {
    /**
     * Returns the text of this script. Returns null if this script has no text.
     */
    String getText();

    /**
     * Returns the name to use for the compiled class for this script. Never returns null.
     */
    String getClassName();

    /**
     * Returns the source file for this script, if any. Return nulls if there is no source file for this script..
     */
    File getSourceFile();

    /**
     * Returns the description for this script. Never returns null.
     */
    String getDescription();
}
