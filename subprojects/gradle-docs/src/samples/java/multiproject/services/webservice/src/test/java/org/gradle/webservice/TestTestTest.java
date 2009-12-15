package org.gradle.webservice;

import junit.framework.TestCase;

/**
 * @author Hans Dockter
 */
public class TestTestTest extends TestCase {
    public void testClasspath() {
        new TestTest().method();
    }

    public void testApiCompileClasspath() {
        new org.gradle.api.PersonList();
    }
}
