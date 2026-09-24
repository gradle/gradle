package org.gradle.sample;

public class Main {
    public static void main(String[] args) {
        if (System.getProperty("greeting.language").equals("en")) {
            System.out.println("Greetings from the sample application.");
        } else {
            System.out.println("Bonjour, monde!");
        }
    }
}
