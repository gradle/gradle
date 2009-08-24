package org.gradle;

import org.junit.Test;
import java.io.File;
import java.util.List;
import static org.junit.Assert.*;
import org.apache.commons.collections.list.GrowthList;

public class PersonIntegrationTest {
    @Test
    public void canConstructAPersonWithAName() {
        // check our classpath
        List list = new GrowthList();

        // check Jar exists
        File jarFile = new File(System.getProperty("jar.path"));
        assertTrue(jarFile.isFile());

        Person person = PersonTestFixture.create("Larry");
        assertEquals("Larry", person.getName());
    }
}
