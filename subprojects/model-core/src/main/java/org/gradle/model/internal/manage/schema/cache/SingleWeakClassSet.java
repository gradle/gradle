/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.cache;

import java.lang.ref.WeakReference;

class SingleWeakClassSet extends WeakClassSet {

    private final WeakReference<Class<?>> reference;
    private final int hash;

    SingleWeakClassSet(Class<?> clazz) {
        this.reference = new WeakReference<Class<?>>(clazz);
        this.hash = clazz.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        Class<?> clazz = reference.get();
        if (clazz == null) {
            return false; // can't be equal otherwise wouldn't have been collected
        }
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SingleWeakClassSet that = (SingleWeakClassSet) o;
        return clazz.equals(that.reference.get());
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    boolean isCollected() {
        Class<?> referent = reference.get();
        return referent == null;
    }

}
