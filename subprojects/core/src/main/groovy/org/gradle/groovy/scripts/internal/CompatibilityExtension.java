/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.groovy.scripts.internal;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This class contains extension methods that are added to the Gradle DSL
 * in order to maintain backwards compatibility with older versions of
 * Gradle which used older versions of Groovy.
 */
public class CompatibilityExtension {
    /**
     * Added to prevent the following error from happening:
     * <pre>
     *     Cannot resolve which method to invoke for [null] due to overlapping prototypes between:
     *     [interface java.util.Collection]
     *     [interface java.lang.Iterable]
     *     [interface java.util.Iterator]> is a groovy.lang.GroovyRuntimeException
     *     </pre>
     */
    @SuppressWarnings("unchecked")
    public static <T> boolean addAll(List<T> collection, Object o) {
        if (o==null || o instanceof Collection) {
            return collection.addAll((Collection<T>) o);
        } else if (o instanceof Iterable) {
            boolean s = true;
            boolean added = false;
            for (Object e : (Iterable) o) {
                added = true;
                s &= collection.add((T) e);
            }
            return added && s;
        } else if (o instanceof Iterator) {
            Iterator<T> it = (Iterator<T>) o;
            boolean s = true;
            boolean added = false;
            while (it.hasNext()) {
                added = true;
                s &= collection.add(it.next());
            }
            return added && s;
        } else if (o.getClass().isArray()) {
            // we're using DGM#asType here because primitive arrays cannot be cast to Object[]
            return Collections.addAll(collection, (T[]) DefaultGroovyMethods.asType(o, Object[].class));
        }
        return collection.add((T) o);
    }
}
