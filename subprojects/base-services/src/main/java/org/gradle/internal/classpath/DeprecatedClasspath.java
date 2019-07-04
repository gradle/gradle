/*
 * Copyright 2019 the original author or authors.
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

public class DeprecatedClasspath implements ClassPath {

    private final ClassPath delegate;

    private DeprecatedClasspath(ClassPath delegate) {
        this.delegate = delegate;
    }

    public static ClassPath of(ClassPath classpath) {
        return new DeprecatedClasspath(classpath);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public List<URI> getAsURIs() {
        return delegate.getAsURIs();
    }

    @Override
    public List<File> getAsFiles() {
        return delegate.getAsFiles();
    }

    @Override
    public List<URL> getAsURLs() {
        return delegate.getAsURLs();
    }

    @Override
    public URL[] getAsURLArray() {
        return delegate.getAsURLArray();
    }

    @Override
    public ClassPath plus(Collection<File> classPath) {
        return of(delegate.plus(classPath));
    }

    @Override
    public ClassPath plus(ClassPath classPath) {
        return of(delegate.plus(classPath));
    }

    @Override
    public ClassPath removeIf(Spec<? super File> filter) {
        return of(delegate.removeIf(filter));
    }

}
