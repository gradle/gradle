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

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A JUnit rule which restores system properties at the end of the test.
 */
public class SetSystemProperties implements MethodRule {
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

    public Statement apply(final Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                System.getProperties().putAll(customProperties);
                try {
                    base.evaluate();
                } finally {
                    System.setProperties(properties);
                }
            }
        };
    }
}
