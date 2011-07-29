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
package org.gradle.plugins.cpp.internal;

import org.gradle.plugins.cpp.CppSourceSet;

import org.gradle.api.file.SourceDirectorySet;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.util.ConfigureUtil;

import groovy.lang.Closure;

public class DefaultCppSourceSet implements CppSourceSet {

    private final String name;
    private final FileResolver fileResolver;

    private final DefaultSourceDirectorySet headers;
    private final DefaultSourceDirectorySet source;

    public DefaultCppSourceSet(String name, FileResolver fileResolver) {
        this.name = name;
        this.fileResolver = fileResolver;

        this.headers = new DefaultSourceDirectorySet("headers", fileResolver);
        this.source = new DefaultSourceDirectorySet("source", fileResolver);
    }

    public String getName() {
        return name;
    }

    public SourceDirectorySet getHeaders() {
        return headers;
    }

    public DefaultCppSourceSet headers(Closure closure) {
        ConfigureUtil.configure(closure, headers);
        return this;
    }

    public SourceDirectorySet getSource() {
        return source;
    }

    public DefaultCppSourceSet source(Closure closure) {
        ConfigureUtil.configure(closure, source);
        return this;
    }
}