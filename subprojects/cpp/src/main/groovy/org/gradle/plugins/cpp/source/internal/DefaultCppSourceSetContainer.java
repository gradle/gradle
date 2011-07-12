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
package org.gradle.plugins.cpp.source.internal;

import org.gradle.plugins.cpp.source.CppSourceSet;
import org.gradle.plugins.cpp.source.CppSourceSetContainer;

import org.gradle.api.internal.AutoCreateDomainObjectContainer;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.file.FileResolver;

import groovy.lang.Closure;

public class DefaultCppSourceSetContainer extends AutoCreateDomainObjectContainer<CppSourceSet> implements CppSourceSetContainer {
    private final FileResolver fileResolver;
    private final ClassGenerator generator;

    public DefaultCppSourceSetContainer(FileResolver fileResolver, ClassGenerator classGenerator) {
        super(CppSourceSet.class, classGenerator);
        this.fileResolver = fileResolver;
        this.generator = classGenerator;
    }

    protected CppSourceSet create(String name) {
        return generator.newInstance(DefaultCppSourceSet.class, name, fileResolver);
    }

    public DefaultCppSourceSetContainer configure(Closure closure) {
        return (DefaultCppSourceSetContainer)super.configure(closure);
    }
}