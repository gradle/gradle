package org.gradle;

import java.util.logging.Logger;

public class OtherTest {
    @org.junit.Test
    public void ok() throws Exception {
        // check logging
        Logger.getLogger("test-logger").warning("this is another warning");
    }
}