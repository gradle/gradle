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
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.DynamicObject;
import org.gradle.api.internal.DynamicObjectUtil;

import java.util.Collection;
import java.util.Map;

import static org.gradle.util.CollectionUtils.toStringList;

public class ConfigureUtil {

    public static <T> T configureByMap(Map<?, ?> properties, T delegate) {
        DynamicObject dynamicObject = DynamicObjectUtil.asDynamicObject(delegate);

        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String name = entry.getKey().toString();
            Object value = entry.getValue();

            if (dynamicObject.hasProperty(name)) {
                dynamicObject.setProperty(name, value);
            } else {
                try {
                    dynamicObject.invokeMethod(name, value);
                } catch (MissingMethodException e) {
                    dynamicObject.setProperty(name, value);
                }
            }
        }

        return delegate;
    }

    public static <T> T configureByMap(Map<?, ?> properties, T delegate, Collection<?> mandatoryKeys) {
        if (!mandatoryKeys.isEmpty()) {
            Collection<String> missingKeys = toStringList(mandatoryKeys);
            missingKeys.removeAll(toStringList(properties.keySet()));
            if (!missingKeys.isEmpty()) {
                throw new IncompleteInputException("Input configuration map does not contain following mandatory keys: " + missingKeys, missingKeys);
            }
        }
        return configureByMap(properties, delegate);
    }

    public static class IncompleteInputException extends RuntimeException {
        private final Collection missingKeys;

        public IncompleteInputException(String message, Collection missingKeys) {
            super(message);
            this.missingKeys = missingKeys;
        }

        public Collection getMissingKeys() {
            return missingKeys;
        }
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
        ClosureBackedAction<T> action = new ClosureBackedAction<T>(configureClosure, resolveStrategy, configureableAware);
        action.execute(delegate);
        return delegate;
    }
}
