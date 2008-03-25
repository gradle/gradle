package org.gradle.api.tasks.testing;

/**
 * Created by IntelliJ IDEA.
 * User: hans
 * Date: Feb 8, 2008
 * Time: 9:17:18 AM
 * To change this template use File | Settings | File Templates.
 */
public enum ForkMode {
    PER_TEST("perTest"), ONCE("once");

    ForkMode(String name) {
        this.name = name;
    }

    String name;

    public String toString() {
        return name;
    }
}
