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
import groovy.lang.MissingPropertyException;
import org.gradle.api.internal.*;

import java.util.Collection;
import java.util.Map;

import static org.gradle.util.CollectionUtils.toStringList;

public class ConfigureUtil {

    public static <T> T configureByMap(Map<?, ?> properties, T delegate) {
        DynamicObject dynamicObject = DynamicObjectUtil.asDynamicObject(delegate);

        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String name = entry.getKey().toString();
            Object value = entry.getValue();

            SetPropertyResult setterResult = new SetPropertyResult();
            dynamicObject.setProperty(name, value, setterResult);
            if (setterResult.isFound()) {
                continue;
            }

            InvokeMethodResult invokeResult = new InvokeMethodResult();
            dynamicObject.invokeMethod(name, invokeResult, value);
            if (!invokeResult.isFound()) {
                throw new MissingPropertyException(name, delegate.getClass());
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
     * @param target The object to be configured
     * @return The delegate param
     */
    public static <T> T configure(Closure configureClosure, T target) {
        ClosureBackedAction<T> action = new ClosureBackedAction<T>(configureClosure, Closure.DELEGATE_FIRST, true);
        action.execute(target);
        return target;
    }

    /**
     * Called from an object's {@link Configuable#configure} method.
     */
    public static <T> T configureSelf(Closure configureClosure, T target) {
        return configureSelf(configureClosure, target, new ConfigureDelegate(configureClosure.getOwner(), target));
    }

    /**
     * Called from an object's {@link Configuable#configure} method.
     */
    public static <T> T configureSelf(Closure configureClosure, T target, ConfigureDelegate closureDelegate) {
        // Hackery to make closure execution faster, by short-circuiting the expensive property and method lookup on Closure
        Closure withNewOwner = configureClosure.rehydrate(target, closureDelegate, configureClosure.getThisObject());
        ClosureBackedAction<T> action = new ClosureBackedAction<T>(withNewOwner, Closure.OWNER_ONLY, false);
        action.execute(target);
        return target;
    }
}
