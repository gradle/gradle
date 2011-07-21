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
package org.gradle.api.internal;

import org.gradle.api.Namer;
import java.util.Comparator;

class NamerComparator<T> implements Comparator<T> {

    private final Namer<? super T> namer;

    public NamerComparator(Namer<? super T> namer) {
        this.namer = namer;
    }

    public int compare(T o1, T o2) {
        return namer.determineName(o1).compareTo(namer.determineName(o2));
    }

    public boolean equals(Object obj) {
        return getClass().equals(obj.getClass()) && namer.equals(((NamerComparator)obj).namer);
    }

    public int hashCode() {
        return 31 * namer.hashCode();
    }
}