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
package org.gradle.plugins.cpp.model.internal;

import org.gradle.plugins.cpp.model.NativeSourceSet;

import org.gradle.api.file.SourceDirectorySet;

import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;

import org.gradle.util.GUtil;

public class DefaultNativeSourceSet extends AbstractNamedDomainObjectContainer<SourceDirectorySet> implements NativeSourceSet {

    final private String name;
    final private String displayName;
    final private FileResolver fileResolver;

    public DefaultNativeSourceSet(String name, ClassGenerator classGenerator, FileResolver fileResolver) {
        super(SourceDirectorySet.class, classGenerator);
        this.name = name;
        this.displayName = GUtil.toWords(this.name);
        this.fileResolver = fileResolver;
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String toString() {
        return String.format("native source set %s", getDisplayName());
    }

    protected SourceDirectorySet doCreate(String name) {
        return getClassGenerator().newInstance(DefaultSourceDirectorySet.class, name, String.format("%s > %s source directory set", this, name), fileResolver);
    }

}