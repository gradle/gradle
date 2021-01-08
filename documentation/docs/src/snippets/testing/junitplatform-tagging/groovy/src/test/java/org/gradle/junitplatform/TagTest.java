package org.gradle.junitplatform;

import org.junit.jupiter.api.*;

public class TagTest {
    @Fast
    @Test
    public void fastTest() {
    }

    @Tag("slow")
    @Test
    public void slowTest(){
    }
}
