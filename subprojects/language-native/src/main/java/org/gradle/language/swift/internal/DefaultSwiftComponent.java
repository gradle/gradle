/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent;
import org.gradle.language.swift.SwiftComponent;

import java.util.Collections;

public abstract class DefaultSwiftComponent extends DefaultNativeComponent implements SwiftComponent {
    private final FileCollection swiftSource;
    private final ConfigurableFileCollection importPath;
    private final ConfigurableFileCollection linkLibraries;
    private final PropertyState<String> module;
    private final String name;

    public DefaultSwiftComponent(String name, FileOperations fileOperations, ProviderFactory providerFactory) {
        super(fileOperations);
        this.name = name;
        swiftSource = createSourceView("src/"+ name + "/swift", Collections.singletonList("swift"));
        importPath = fileOperations.files();
        linkLibraries = fileOperations.files();
        module = providerFactory.property(String.class);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PropertyState<String> getModule() {
        return module;
    }

    @Override
    public FileCollection getSwiftSource() {
        return swiftSource;
    }

    @Override
    public ConfigurableFileCollection getLinkLibraries() {
        return linkLibraries;
    }

    @Override
    public ConfigurableFileCollection getCompileImportPath() {
        return importPath;
    }
}
