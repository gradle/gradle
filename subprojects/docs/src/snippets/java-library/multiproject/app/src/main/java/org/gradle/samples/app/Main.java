package org.gradle.samples.app;

import org.gradle.samples.core.Person;
import org.gradle.samples.utils.PersonUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {

    public static String readLine(String label) throws Exception {
        System.out.print(label);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }

    public static void main(String... args) throws Exception {
        String lastName = readLine("Last Name: ");
        String firstName = readLine("First Name: ");
        String dateOfBirth = readLine("Date of birth (dd/mm/YYYY): ");
        Person person = PersonUtils.of(firstName, lastName, dateOfBirth);
        System.out.println(person);
    }
}
