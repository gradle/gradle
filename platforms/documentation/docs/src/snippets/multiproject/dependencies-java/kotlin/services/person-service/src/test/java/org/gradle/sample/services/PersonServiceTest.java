package org.gradle.sample.services;

import junit.framework.TestCase;
import org.gradle.sample.apiImpl.PersonImpl;

public class PersonServiceTest extends TestCase {
    public void testFindPerson() {
        PersonImpl testPerson = new PersonImpl("Build", "Master");
        assertTrue(new PersonService().checkPerson(testPerson));
    }
}
