/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.internal.prebuilt;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PrebuiltLibrary;

public class DefaultPrebuiltLibrary implements PrebuiltLibrary {

    private final String name;
    private final SourceDirectorySet headers;
    private final DomainObjectSet<NativeLibraryBinary> binaries;

    public DefaultPrebuiltLibrary(String name, ObjectFactory objectFactory, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        this.name = name;
        headers = objectFactory.sourceDirectorySet("headers", "headers for prebuilt library '" + name + "'");
        binaries = domainObjectCollectionFactory.newDomainObjectSet(NativeLibraryBinary.class);
    }

    @Override
    public String toString() {
        return "prebuilt library '" + name + "'";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SourceDirectorySet getHeaders() {
        return headers;
    }

    @Override
    public DomainObjectSet<NativeLibraryBinary> getBinaries() {
        return binaries;
    }
}
