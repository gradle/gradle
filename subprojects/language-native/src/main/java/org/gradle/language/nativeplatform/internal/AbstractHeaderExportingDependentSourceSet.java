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
package org.gradle.language.nativeplatform.internal;

import com.google.common.collect.Sets;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A convenience base class for implementing language source sets with dependencies and exported headers.
 */
public abstract class AbstractHeaderExportingDependentSourceSet extends AbstractHeaderExportingSourceSet
        implements HeaderExportingSourceSet, LanguageSourceSet, DependentSourceSet {

    private final List<Object> libs = new ArrayList<Object>();
    private Set<String> preCompiledHeaders = Sets.newLinkedHashSet();
    private File prefixHeaderFile;

    public Collection<?> getLibs() {
        return libs;
    }

    public void lib(Object library) {
        if (library instanceof Iterable<?>) {
            Iterable<?> iterable = (Iterable) library;
            CollectionUtils.addAll(libs, iterable);
        } else {
            libs.add(library);
        }
    }

    @Override
    public Set<String> getPreCompiledHeaders() {
        return preCompiledHeaders;
    }

    @Override
    public void preCompiledHeader(String header) {
        this.preCompiledHeaders.add(header);
    }

    @Override
    public File getPrefixHeaderFile() {
        return prefixHeaderFile;
    }

    @Override
    public void setPrefixHeaderFile(File prefixHeaderFile) {
        this.prefixHeaderFile = prefixHeaderFile;
    }
}