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
 * Types that are to be used with a {@link NamedDomainObjectContainer} can implement this interface to achieve implicit naming.
 *
 * @see FactoryNamedDomainObjectContainer#FactoryNamedDomainObjectContainer(Class, ClassGenerator)
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

    public static class Namer implements org.gradle.api.Namer<Named> {
        public String determineName(Named object) {
            return object.getName();
        }
    }
}
