/**
 * Created by IntelliJ IDEA.
 * User: hans
 * Date: Feb 21, 2008
 * Time: 3:38:09 PM
 * To change this template use File | Settings | File Templates.
 */
package org.gradle.api.tasks.bundling;

public class Compression {
    public static final Compression NONE = new Compression("", "none");
    public static final Compression GZIP = new Compression(".gz", "gzip");
    public static final Compression BZIP2 = new Compression(".bzip2", "bzip2");

    private final String extension;

    private final String antValue;

    private Compression(String extension, String antValue) {
        this.extension = extension;
        this.antValue = antValue;
    }

    public String getExtension() {
        return extension;
    }

    public String getAntValue() {
        return antValue;
    }
}
