package org.gradle.junit;

import org.junit.Assert;
import org.junit.Test;

public class SimpleJUnitTest {
    @Test
    public void ok() {
        Assert.assertTrue(true);
    }

    @Test
    public void ignoreThisTest() {
        Assert.fail("This test must be ignored by our custom runner");
    }
}
