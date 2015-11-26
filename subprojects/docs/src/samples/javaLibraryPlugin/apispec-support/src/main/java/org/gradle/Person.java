package org.gradle;

public class Person {
    private final String name;

    public Person(String name) {
        // we updated the body if this method
        // but the signature doesn't change
        // so we will not recompile components
        // that depend on this class
        this.name = name.toUpperCase();
    }

    public String getName() {
        return name;
    }
}
