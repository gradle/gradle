package org.gradle;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class IgnoredTest {
    @Test
    public void testIgnored1() {
        throw new RuntimeException();
    }

    @Test
    public void testIgnored2() {
    }
}
