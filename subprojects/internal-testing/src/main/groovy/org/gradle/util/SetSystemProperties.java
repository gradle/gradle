/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.util;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A JUnit rule which restores system properties at the end of the test.
 */
public class SetSystemProperties implements TestRule {
    private final static Logger LOGGER = LoggerFactory.getLogger(SetSystemProperties.class);
    private final Properties properties;
    private final Map<String, Object> customProperties = new HashMap<String, Object>();

    public SetSystemProperties() {
        properties = new Properties();
        properties.putAll(System.getProperties());
    }

    public SetSystemProperties(Map<String, Object> properties) {
        this();
        customProperties.putAll(properties);
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                System.getProperties().putAll(customProperties);
                resetTempDirLocation();
                try {
                    base.evaluate();
                } finally {
                    System.setProperties(properties);
                    resetTempDirLocation();
                }
            }
        };
    }

    public static void resetTempDirLocation() {
        File tmpdirFile = new File(System.getProperty("java.io.tmpdir"));
        // reset cache in File.createTempFile
        try {
            Field tmpdirField = Class.forName("java.io.File$TempDirectory").getDeclaredField("tmpdir");
            makeFinalFieldAccessible(tmpdirField);
            tmpdirField.set(null, tmpdirFile);
        } catch (Exception e) {
            LOGGER.warn("Cannot reset tmpdir field used by java.io.File.createTempFile");
        }
        // reset cache in Files.createTempFile
        try {
            Field tmpdirField = Class.forName("java.nio.file.TempFileHelper").getDeclaredField("tmpdir");
            makeFinalFieldAccessible(tmpdirField);
            tmpdirField.set(null, tmpdirFile.toPath());
        } catch (Exception e) {
            LOGGER.warn("Cannot reset tmpdir field used by java.nio.file.Files.createTempFile");
        }
    }

    private static void makeFinalFieldAccessible(Field field) throws NoSuchFieldException, IllegalAccessException {
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }
}
