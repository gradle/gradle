package org.gradle;

import java.util.logging.Logger;

public class OtherTest {
    static {
        System.out.println("other class loaded");
    }

    public OtherTest() {
        System.out.println("other test constructed");
    }

    @org.junit.Test
    public void ok() throws Exception {
        // check stdout and stderr
        System.out.println("This is other stdout");
        System.err.println("This is other stderr");
        // check logging
        Logger.getLogger("test-logger").warning("this is another warning");
    }
}