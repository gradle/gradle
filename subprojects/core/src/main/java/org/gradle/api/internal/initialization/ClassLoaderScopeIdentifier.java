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

package org.gradle.api.internal.initialization;

import com.google.common.base.Joiner;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.initialization.ClassLoaderScopeId;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class ClassLoaderScopeIdentifier implements ClassLoaderScopeId {

    @Nullable
    private final ClassLoaderScopeIdentifier parent;
    private final String name;

    public ClassLoaderScopeIdentifier(@Nullable ClassLoaderScopeIdentifier parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    public ClassLoaderScopeIdentifier child(String name) {
        return new ClassLoaderScopeIdentifier(this, name);
    }

    @Override
    @Nullable
    public ClassLoaderScopeIdentifier getParent() {
        return parent;
    }

    @Override
    public String getName() {
        return name;
    }

    public ClassLoaderId localId() {
        return new Id(this, false);
    }

    ClassLoaderId exportId() {
        return new Id(this, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClassLoaderScopeIdentifier that = (ClassLoaderScopeIdentifier) o;

        return name.equals(that.name) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(parent);
        result = 31 * result + name.hashCode();
        return result;
    }

    String getPath() {
        Deque<String> names = new ArrayDeque<>();
        names.add(name);
        ClassLoaderScopeIdentifier nextParent = parent;
        while (nextParent != null) {
            names.addFirst(nextParent.name);
            nextParent = nextParent.parent;
        }
        return Joiner.on(":").join(names);
    }

    @Override
    public String toString() {
        return "ClassLoaderScopeIdentifier{" + getPath() + "}";
    }

    private static class Id implements ClassLoaderId {
        private final ClassLoaderScopeIdentifier identifier;
        private final boolean export;

        public Id(ClassLoaderScopeIdentifier identifier, boolean export) {
            this.identifier = identifier;
            this.export = export;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Id id = (Id) o;
            return export == id.export && identifier.equals(id.identifier);
        }

        @Override
        public int hashCode() {
            int result = identifier.hashCode();
            result = 31 * result + Boolean.hashCode(export);
            return result;
        }

        @Override
        public String getDisplayName() {
            return identifier.getPath() + "(" + (export ? "export" : "local") + ")";
        }

        @Override
        public String toString() {
            return "ClassLoaderScopeIdentifier.Id{" + getDisplayName() + "}";
        }
    }
}
