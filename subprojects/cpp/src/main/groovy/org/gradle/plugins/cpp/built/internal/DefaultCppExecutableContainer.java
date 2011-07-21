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

import org.gradle.api.Namer;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.ClassGenerator;

import org.gradle.plugins.cpp.built.CppExecutable;
import org.gradle.plugins.cpp.built.CppExecutableContainer;

public class DefaultCppExecutableContainer extends DefaultNamedDomainObjectSet<CppExecutable> implements CppExecutableContainer {

    public DefaultCppExecutableContainer(ClassGenerator classGenerator) {
        super(CppExecutable.class, classGenerator, new Namer<CppExecutable>() { public String determineName(CppExecutable e) { return e.getName(); }});
    }

}