package org.gradle;

import junit.framework.TestCase;
import org.junit.Test;

public class TestsOnInner {
    @Test public void ok() { }

    public static class SomeInner {
        @Test public void ok() { }
    }

    public class NonStaticInner {
        @Test public void ok() { }
    }

    public class NonStaticInnerTestCase extends TestCase {
        public void testOk() { }
    }
}
