/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api;

/**
 * Types can implement this interface and use the embedded {@link Namer} implementation, to satisfy API that calls for a namer.
 */
public interface Named {

    /**
     * The object's name.
     * <p>
     * Must be constant for the life of the object.
     *
     * @return The name. Never null.
     */
    String getName();

    // -- Internal note --
    // It would be better to only require getName() to return Object and just call toString() on it, but
    // if you have a groovy class with a “String name” property the generated getName() method will not 
    // satisfy the Named interface. This seems to be a bug in the Groovy compiler - LD.

    /**
     * An implementation of the namer interface for objects implementing the named interface.
     */
    public static class Namer implements org.gradle.api.Namer<Named> {

        public static final org.gradle.api.Namer<Named> INSTANCE = new Namer();

        public String determineName(Named object) {
            return object.getName();
        }

        @SuppressWarnings("unchecked")
        public static <T> org.gradle.api.Namer<? super T> forType(Class<? extends T> type) {
            if (Named.class.isAssignableFrom(type)) {
                return (org.gradle.api.Namer<T>) INSTANCE;
            } else {
                throw new IllegalArgumentException(String.format("The '%s' cannot be used with FactoryNamedDomainObjectContainer without specifying a Namer as it does not implement the Named interface.", type));
            }
        }
    }
}
