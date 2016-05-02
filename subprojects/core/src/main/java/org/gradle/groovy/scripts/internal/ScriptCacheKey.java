/*
 * Copyright 2016 the original author or authors.
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

class ScriptCacheKey {
    private final String className;
    private final ClassLoader classLoader;
    private final String dslId;

    public ScriptCacheKey(String className, ClassLoader classLoader, String dslId) {
        this.className = className;
        this.classLoader = classLoader;
        this.dslId = dslId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ScriptCacheKey key = (ScriptCacheKey) o;

        return classLoader.equals(key.classLoader)
            && className.equals(key.className)
            && dslId.equals(key.dslId);
    }

    @Override
    public int hashCode() {
        int result = className.hashCode();
        result = 31 * result + classLoader.hashCode();
        result = 31 * result + dslId.hashCode();
        return result;
    }
}
