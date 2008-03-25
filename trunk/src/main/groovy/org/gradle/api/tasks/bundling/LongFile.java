/**
 * Created by IntelliJ IDEA.
 * User: hans
 * Date: Feb 21, 2008
 * Time: 9:12:10 PM
 * To change this template use File | Settings | File Templates.
 */
package org.gradle.api.tasks.bundling;

public class LongFile {
    public static final LongFile TRUNCATE = new LongFile("truncate");
    public static final LongFile WARN = new LongFile("warn");
    public static final LongFile GNU = new LongFile("gnu");
    public static final LongFile OMIT = new LongFile("omit");
    public static final LongFile FAIL = new LongFile("fail");

    private final String antValue; 

    private LongFile(String name) {
        antValue = name;
    }

    public String getAntValue() {
        return antValue;
    }
}
