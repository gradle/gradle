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

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class ConfigureUtil {

    public static <T> T configure(Closure configureClosure, T delegate) {
        return configure(configureClosure, delegate, Closure.DELEGATE_FIRST);
    }

    public static <T> T configureByMap(Map<String, ?> properties, T delegate) {
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            try {
                ReflectionUtil.setProperty(delegate, entry.getKey(), entry.getValue());
            } catch (MissingPropertyException e) {
                // Try as a method
                try {
                    ReflectionUtil.invoke(delegate, entry.getKey(), new Object[]{entry.getValue()});
                } catch (MissingMethodException mme) {
                    // Throw the original MPE
                    throw e;
                }
            }
        }
        return delegate;
    }

    private static <T> T configure(Closure configureClosure, T delegate, int resolveStrategy) {
        if (configureClosure == null) {
            return delegate;
        }
        Closure copy = (Closure) configureClosure.clone();
        copy.setResolveStrategy(resolveStrategy);
        copy.setDelegate(delegate);
        if (copy.getMaximumNumberOfParameters() == 0) {
            copy.call();
        } else {
            copy.call(delegate);
        }
        return delegate;
    }
}
