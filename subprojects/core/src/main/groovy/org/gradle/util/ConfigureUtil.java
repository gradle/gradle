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

    /**
     * <p>Configures {@code delegate} with {@code configureClosure}, via the {@link Configurable} interface if necessary.</p>
     * 
     * <p>If {@code delegate} does not implement {@link Configurable} interface, it is set as the delegate of a clone of 
     * {@code configureClosure} with a resolve strategy of {@code DELEGATE_FIRST}.</p>
     * 
     * <p>If {@code delegate} does implement the {@link Configurable} interface, the {@code configureClosure} will be passed to
     * {@code delegate}'s {@link Configurable#configure(Closure)} method.</p>
     * 
     * @param configureClosure The configuration closure
     * @param delegate The object to be configured
     * @return The delegate param
     */
    public static <T> T configure(Closure configureClosure, T delegate) {
        return configure(configureClosure, delegate, Closure.DELEGATE_FIRST, true);
    }

    /**
     * <p>Configures {@code delegate} with {@code configureClosure}, via the {@link Configurable} interface if necessary.</p>
     * 
     * <p>If {@code delegate} does not implement {@link Configurable} interface, it is set as the delegate of a clone of 
     * {@code configureClosure} with a resolve strategy of {@code DELEGATE_FIRST}.</p>
     * 
     * <p>If {@code delegate} does implement the {@link Configurable} interface, the {@code configureClosure} will be passed to
     * {@code delegate}'s {@link Configurable#configure(Closure)} method. However, if {@code configureableAware} is false then
     * {@code delegate} will be treated like it does not implement the configurable interface.</p>
     * 
     * @param configureClosure The configuration closure
     * @param delegate The object to be configured
     * @param configureableAware Whether or not to use the {@link Configurable} interface to configure the object if possible
     * @return The delegate param
     */
    public static <T> T configure(Closure configureClosure, T delegate, boolean configureableAware) {
        return configure(configureClosure, delegate, Closure.DELEGATE_FIRST, configureableAware);
    }

    /**
     * <p>Configures {@code delegate} with {@code configureClosure}, ignoring the {@link Configurable} interface.</p>
     * 
     * <p>{@code delegate} is set as the delegate of a clone of {@code configureClosure} with a resolve strategy 
     * of the {@code resolveStrategy} param.</p>
     * 
     * @param configureClosure The configuration closure
     * @param delegate The object to be configured
     * @param resolveStrategy The resolution strategy to use for the configuration closure
     * @return The delegate param
     */
    public static <T> T configure(Closure configureClosure, T delegate, int resolveStrategy) {
        return configure(configureClosure, delegate, resolveStrategy, false);
    }

    private static <T> T configure(Closure configureClosure, T delegate, int resolveStrategy, boolean configureableAware) {
        if (configureClosure == null) {
            return delegate;
        }

        if (configureableAware && delegate instanceof Configurable) {
            ((Configurable)delegate).configure(configureClosure);
        } else {
            Closure copy = (Closure) configureClosure.clone();
            copy.setResolveStrategy(resolveStrategy);
            copy.setDelegate(delegate);
            if (copy.getMaximumNumberOfParameters() == 0) {
                copy.call();
            } else {
                copy.call(delegate);
            }
        }

        return delegate;
    }
}
