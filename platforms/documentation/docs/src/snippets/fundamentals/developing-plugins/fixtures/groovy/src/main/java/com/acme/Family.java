package com.acme;

import java.util.List;
import java.util.Arrays;

public class Family {
    private final List<Person> members;

    public Family(Person... members) {
        this.members = Arrays.asList(members);
    }

    public List<Person> getMembers() {
        return members;
    }

    public int size() {
        return members.size();
    }

    public boolean contains(Person person) {
        return members.contains(person);
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "Family{" +
            "members=" + members +
            '}';
    }
}
