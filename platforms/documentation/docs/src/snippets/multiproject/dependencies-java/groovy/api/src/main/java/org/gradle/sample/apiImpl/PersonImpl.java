package org.gradle.sample.apiImpl;

import org.gradle.sample.api.Person;
import org.gradle.sample.shared.Helper;

public class PersonImpl implements Person {
    private String firstname;
    private String surname;

    public PersonImpl(String surname, String firstname) {
        this.surname = surname;
        this.firstname = firstname;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String toString() {
        return Helper.prettyPrint(firstname + " " + surname);
    }
}
