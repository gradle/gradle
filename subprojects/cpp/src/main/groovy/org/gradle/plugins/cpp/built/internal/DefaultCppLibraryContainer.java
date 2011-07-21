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
package org.gradle.plugins.cpp.built.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DefaultNamedDomainObjectContainer;
import org.gradle.api.internal.ClassGenerator;

import org.gradle.plugins.cpp.built.CppLibrary;
import org.gradle.plugins.cpp.built.CppLibraryContainer;

public class DefaultCppLibraryContainer extends DefaultNamedDomainObjectContainer<CppLibrary> implements CppLibraryContainer {

    public DefaultCppLibraryContainer(ClassGenerator classGenerator) {
        super(CppLibrary.class, classGenerator);
    }
    
    public CppLibrary add(CppLibrary library) throws InvalidUserDataException {
        addObject(library.getName(), library);
        return library;
    }
}