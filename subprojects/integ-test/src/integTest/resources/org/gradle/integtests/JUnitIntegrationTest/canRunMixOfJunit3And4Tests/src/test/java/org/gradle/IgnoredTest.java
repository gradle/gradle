package org.gradle;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class IgnoredTest {
    @Test
    public void testIgnored() {
        throw new RuntimeException();
    }
}
