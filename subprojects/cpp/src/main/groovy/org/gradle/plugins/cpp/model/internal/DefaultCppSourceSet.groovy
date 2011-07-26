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
package org.gradle.plugins.cpp.model.internal

import org.gradle.plugins.cpp.model.CppSourceSet

import org.gradle.api.file.SourceDirectorySet

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.DefaultSourceDirectorySet

import org.gradle.util.GUtil
import org.gradle.util.ConfigureUtil

class DefaultCppSourceSet implements CppSourceSet {

    private String name
    private String displayName

    private final SourceDirectorySet cppSource
    private final SourceDirectorySet headerSource

    DefaultCppSourceSet(String name, FileResolver fileResolver) {
        this.name = name
        this.displayName = GUtil.toWords(this.name)

        def cppSourceDisplayName = String.format("%s C++ source", displayName)
        cppSource = new DefaultSourceDirectorySet(cppSourceDisplayName, fileResolver)
        cppSource.filter.include("**/*.cpp")

        def headerSourceDisplayName = String.format("%s header files", displayName)
        headerSource = new DefaultSourceDirectorySet(headerSourceDisplayName, fileResolver)
        headerSource.filter.include("**/*.h")
    }

    String getName() {
        name
    }

    String getDisplayName() {
        displayName
    }

    String toString() {
        String.format("C++ source set %s", getDisplayName())
    }

    SourceDirectorySet getCpp() {
        cppSource
    }

    CppSourceSet cpp(Closure closure) {
        ConfigureUtil.configure(closure, getCpp())
        this
    }

    SourceDirectorySet getHeaders() {
        headerSource
    }

    CppSourceSet headers(Closure closure) {
        ConfigureUtil.configure(closure, getHeaders())
        this
    }

}