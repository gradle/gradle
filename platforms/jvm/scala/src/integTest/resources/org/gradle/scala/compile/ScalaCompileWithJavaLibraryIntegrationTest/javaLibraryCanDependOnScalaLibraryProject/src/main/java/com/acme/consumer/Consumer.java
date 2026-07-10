package com.acme.consumer;

import com.acme.lib.Person;

public class Consumer {
    private final Person person;

    public Consumer(Person person) {
        this.person = person;
    }

    public Person getPerson() {
        return person;
    }
}
