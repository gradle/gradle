package org.gradle.api;

import java.util.ArrayList;
import org.gradle.shared.Person;
import org.gradle.apiImpl.Impl;


public class PersonList {
    private ArrayList<Person> persons = new ArrayList<Person>();

    public void doSomethingWithImpl() {
        org.apache.commons.lang.builder.ToStringBuilder stringBuilder;
        try {
             Class.forName("org.apache.commons.io.FileUtils");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        new Impl().implMethod();
    }

}
