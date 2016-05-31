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

package org.gradle.internal.classloader;

import com.google.common.hash.HashCode;

public class HashedClassLoader extends ClassLoader implements ClassLoaderHierarchy {
    private final HashCode hashCode;

    public HashedClassLoader(ClassLoader parent, HashCode hashCode) {
        super(parent);
        this.hashCode = hashCode;
    }

    public HashCode getClassLoaderHash() {
        return hashCode;
    }

    @Override
    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new Spec(hashCode));
        visitor.visitParent(getParent());
    }

    public static ClassLoader unwrap(ClassLoader classLoader) {
        if (classLoader instanceof HashedClassLoader) {
            return unwrap(classLoader.getParent());
        } else {
            return classLoader;
        }
    }

    public static class Spec extends ClassLoaderSpec {
        private final HashCode hashCode;

        public Spec(HashCode hashCode) {
            this.hashCode = hashCode;
        }

        public HashCode getClassLoaderHash() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(HashedClassLoader.Spec.class);
        }

        @Override
        public int hashCode() {
            return getClass().getName().hashCode();
        }
    }
}
