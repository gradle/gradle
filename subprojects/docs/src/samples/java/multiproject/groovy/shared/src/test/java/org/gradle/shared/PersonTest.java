package org.gradle.shared;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Properties;

public class PersonTest extends TestCase {
    public void testTest() {
        Person person = new Person("testname1");
        assertEquals("testname1", person.getName());
        person.setName("testname2");
        assertEquals("testname2", person.getName());
    }

    public void testMainProperty() throws IOException {
        assertEquals("mainValue", new Person("test").readProperty());
    }

    public void testTestProperty() throws IOException {
        Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("org/gradle/shared/test.properties"));
        assertEquals("testValue", properties.getProperty("test"));
    }
}
