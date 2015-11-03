package org.gradle;

import org.gradle.internal.PersonInternal;

public class Client {
    private PersonInternal person;

    public void setPerson(PersonInternal p) { this.person = p; }
    public PersonInternal getPerson() { return person; }
}
