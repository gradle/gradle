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

import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A convenience base class for implementing language source sets with dependencies and exported headers.
 */
public abstract class AbstractHeaderExportingDependentSourceSet extends AbstractHeaderExportingSourceSet
        implements HeaderExportingSourceSet, LanguageSourceSet, DependentSourceSetInternal {

    private final List<Object> libs = new ArrayList<Object>();
    private String preCompiledHeader;
    private File prefixHeaderFile;

    @Override
    public Collection<?> getLibs() {
        return libs;
    }

    @Override
    public void lib(Object library) {
        if (library instanceof Iterable<?>) {
            Iterable<?> iterable = (Iterable) library;
            CollectionUtils.addAll(libs, iterable);
        } else {
            libs.add(library);
        }
    }

    @Override
    public String getPreCompiledHeader() {
        return preCompiledHeader;
    }

    @Override
    public void setPreCompiledHeader(String header) {
        this.preCompiledHeader = header;
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
