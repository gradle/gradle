package org.gradle.sample.utilities;

import org.junit.jupiter.api.Test;

import org.gradle.sample.list.LinkedList;
import org.gradle.sample.utilities.StringUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilsTest {
    @Test public void testSplit() {
        LinkedList list = StringUtils.split("The dog is green");
        assertEquals(4, list.size());
        assertEquals("The", list.get(0));
        assertEquals("dog", list.get(1));
        assertEquals("is", list.get(2));
        assertEquals("green", list.get(3));
    }
}
