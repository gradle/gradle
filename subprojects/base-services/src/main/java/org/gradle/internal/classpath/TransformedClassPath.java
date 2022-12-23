/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.specs.Spec;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;

public class TransformedClassPath implements ClassPath {
    private final ClassPath transformedClassPath;

    public TransformedClassPath(ClassPath transformedClassPath) {
        this.transformedClassPath = transformedClassPath;
    }

    @Override
    public boolean isEmpty() {
        return transformedClassPath.isEmpty();
    }

    @Override
    public List<URI> getAsURIs() {
        return transformedClassPath.getAsURIs();
    }

    @Override
    public List<File> getAsFiles() {
        return transformedClassPath.getAsFiles();
    }

    @Override
    public List<URL> getAsURLs() {
        return transformedClassPath.getAsURLs();
    }

    @Override
    public URL[] getAsURLArray() {
        return transformedClassPath.getAsURLArray();
    }

    ClassPath prepend(DefaultClassPath classPath) {
        return new TransformedClassPath(classPath.plus(transformedClassPath));
    }

    @Override
    public ClassPath plus(Collection<File> classPath) {
        return new TransformedClassPath(transformedClassPath.plus(classPath));
    }

    @Override
    public ClassPath plus(ClassPath classPath) {
        return new TransformedClassPath(transformedClassPath.plus(classPath));
    }

    @Override
    public ClassPath removeIf(Spec<? super File> filter) {
        return new TransformedClassPath(transformedClassPath.removeIf(filter));
    }

    @Override
    public int hashCode() {
        return transformedClassPath.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        TransformedClassPath other = (TransformedClassPath) obj;
        return transformedClassPath.equals(other.transformedClassPath);
    }
}
