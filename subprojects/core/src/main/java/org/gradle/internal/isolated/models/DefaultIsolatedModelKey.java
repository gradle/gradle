/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.isolated.models;

public class DefaultIsolatedModelKey<T> implements IsolatedModelKey<T> {

    private final String name;
    private final Class<T> type;

    public DefaultIsolatedModelKey(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof DefaultIsolatedModelKey)) {
            return false;
        }

        DefaultIsolatedModelKey<?> that = (DefaultIsolatedModelKey<?>) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "IsolatedModelKey{" +
            "name='" + name + '\'' +
            ", type=" + type +
            '}';
    }
}
