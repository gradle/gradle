package org.gradle.junitplatform;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

public class JupiterTest {
    @BeforeAll
    public static void beforeAll() {
        System.out.println("This will be called before all methods!");
    }

    @Test
    public void ok() {
        System.out.println("Hello from JUnit Jupiter!");
    }

    @RepeatedTest(2)
    public void repeated() {
        System.out.println("This will be repeated!");
    }

    @BeforeEach
    public void beforeEach() {
        System.out.println("This will be called before each method!");
    }

    @Disabled
    @Test
    public void disabled() {
        throw new RuntimeException("This won't happen!");
    }

    @Test
    @DisplayName("TEST 1")
    @Tag("my-tag")
    void test1(TestInfo testInfo) {
        assertEquals("TEST 1", testInfo.getDisplayName());
        assertTrue(testInfo.getTags().contains("my-tag"));
    }
}
