package org.gradle.sample;

import java.io.InputStream;
import org.apache.log4j.LogManager;

public class Greeter {
    public String getGreeting() throws Exception {
        LogManager.getRootLogger().info("Just checking if we can use external dependencies....");
        return "hello Gradle";
    }
}
