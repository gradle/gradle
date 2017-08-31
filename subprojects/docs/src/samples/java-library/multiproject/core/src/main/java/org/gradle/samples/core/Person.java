package org.gradle.samples.core;

import java.util.Calendar;
import java.util.Date;
import org.joda.time.LocalDate;
import org.joda.time.Years;

public class Person {
    private final String firstName;
    private final String lastName;
    private final Date dateOfBirth;

    public Person(String firstName, String lastName, Date dateOfBirth) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    public int getAge() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateOfBirth);
        LocalDate birthdate = new LocalDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
        LocalDate now = new LocalDate();
        Years age = Years.yearsBetween(birthdate, now);
        return age.getYears();
    }

    @java.lang.Override
    public String toString() {
        return "Person{" +
            "firstName='" + firstName + '\'' +
            ", lastName='" + lastName + '\'' +
            ", age=" + getAge() +
            '}';
    }
}
