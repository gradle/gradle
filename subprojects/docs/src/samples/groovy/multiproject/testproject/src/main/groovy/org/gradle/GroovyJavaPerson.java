package org.gradle;

import java.util.Properties;
import java.io.IOException;

public class GroovyJavaPerson {
    public String readProperty() throws IOException {
        Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("org/gradle/main.properties"));
        return properties.getProperty("main");
    }
}
