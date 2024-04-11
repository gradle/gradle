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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A JUnit rule which restores system properties at the end of the test.
 */
public class SetSystemProperties implements TestRule {
    private final Properties properties;
    private final Map<String, Object> customProperties = new HashMap<String, Object>();
    private static final String[] IMMUTABLE_SYSTEM_PROPERTIES = new String[]{"java.io.tmpdir"};

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
                validateCustomProperties();
                System.getProperties().putAll(customProperties);
                try {
                    base.evaluate();
                } finally {
                    System.setProperties(properties);
                }
            }
        };
    }

    private void validateCustomProperties() {
        for (String immutableProperty : IMMUTABLE_SYSTEM_PROPERTIES) {
            if (customProperties.containsKey(immutableProperty)) {
                throw new IllegalArgumentException("'" + immutableProperty + "' should not be set via a rule as its value cannot be changed once it is initialized");
            }
        }
    }
}
