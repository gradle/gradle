package org.gradle;

import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.assertEquals;

public class PersonTest{

    Person person;
    @Before public void setup(){
        person = new Person();
    }

    @Test public void testAge() {
        person.setAge(30);
        assertEquals(30, person.getAge());
    }


    @Test public void testSurname() {
        person.setSurname("Duke");
        assertEquals("Duke", person.getSurname());
    }
}
