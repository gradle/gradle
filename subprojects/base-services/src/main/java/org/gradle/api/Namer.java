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
 * A namer is capable of providing a name based on some inherent characteristic of an object.
 *
 * @param <T> The type of object that the namer can name
 */
public interface Namer<T> {

    /**
     * Determines the name of the given object.
     *
     * @param object The object to determine the name of
     * @return The object's inherent name. Never null.
     * @throws RuntimeException If the name cannot be determined or is null
     */
    String determineName(T object);

    /**
     * A comparator implementation based on the names returned by the given namer.
     *
     * @param <T> The type of object that the namer can name
     */
    class Comparator<T> implements java.util.Comparator<T> {

        private final Namer<? super T> namer;

        public Comparator(Namer<? super T> namer) {
            this.namer = namer;
        }

        @Override
        public int compare(T o1, T o2) {
            return namer.determineName(o1).compareTo(namer.determineName(o2));
        }

        public boolean equals(Object obj) {
            return getClass().equals(obj.getClass()) && namer.equals(((Comparator)obj).namer);
        }

        public int hashCode() {
            return 31 * namer.hashCode();
        }
    }
}
