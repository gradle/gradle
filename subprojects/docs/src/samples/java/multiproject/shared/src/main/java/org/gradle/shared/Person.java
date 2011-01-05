package org.gradle.shared;

import java.util.Properties;
import java.io.IOException;

public class Person {
    private String name;

    public Person(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String readProperty() throws IOException {
        Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("org/gradle/shared/main.properties"));
        return properties.getProperty("main");
    }
}
