package org.gradle.samples.mixed;

public class App {
    public static void main(String[] args) {
        Person person = new Person("John Doe");
        Greeter greeter = new Greeter();
        greeter.greet(person);
    }
}