package org.gradle;

import junit.framework.TestCase;

public class Junit3Test extends TestCase {
    public void testRenamesItself() {
        setName("a test that renames itself");
        fail("epic");
    }
}
