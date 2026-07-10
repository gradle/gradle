package org.gradle.sample.utilities;

import org.junit.jupiter.api.Test;

import org.gradle.sample.list.LinkedList;
import org.gradle.sample.utilities.JoinUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JoinUtilsTest {
    @Test public void testJoin() {
        LinkedList list = StringUtils.split("The dog is green");
        assertEquals("The dog is green", JoinUtils.join(list));
    }
}
