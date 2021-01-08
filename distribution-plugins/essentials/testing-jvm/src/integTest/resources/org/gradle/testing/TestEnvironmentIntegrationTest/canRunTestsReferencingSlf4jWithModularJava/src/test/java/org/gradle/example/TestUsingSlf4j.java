package org.gradle.example;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUsingSlf4j {
    @Test public void testModular() {
        Logger logger = LoggerFactory.getLogger(org.gradle.example.TestUsingSlf4j.class);
        logger.info("INFO via slf4j");
        logger.warn("WARN via slf4j");
        logger.error("ERROR via slf4j");
    }
}
