package org.gradle;

import java.util.List;

public class PersonServer {
    private List<Person> persons;

    List<Person> getPersons() { return persons; }
    void setPersons(List<Person> persons) { this.persons = persons; }
}
