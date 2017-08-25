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

package org.gradle.language.cpp.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.nativeplatform.internal.Names;

public class DefaultCppBinary implements CppBinary {
    private final String name;
    private final Provider<String> baseName;
    private final FileCollection sourceFiles;
    private final FileCollection includePath;
    private final FileCollection linkLibraries;

    public DefaultCppBinary(String name, ObjectFactory objects, Provider<String> baseName, FileCollection sourceFiles, FileCollection componentHeaderDirs, ConfigurationContainer configurations, Configuration implementation) {
        this.name = name;
        this.baseName = baseName;
        this.sourceFiles = sourceFiles;

        final Names names = Names.of(name);

        Configuration includePathConfig = configurations.create(names.withPrefix("cppCompile"));
        includePathConfig.setCanBeConsumed(false);
        includePathConfig.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.C_PLUS_PLUS_API));

        Configuration nativeLink = configurations.create(names.withPrefix("nativeLink"));
        nativeLink.setCanBeConsumed(false);
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.NATIVE_LINK));

        Configuration nativeRuntime = configurations.create(names.withPrefix("nativeRuntime"));
        nativeRuntime.setCanBeConsumed(false);
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.NATIVE_RUNTIME));

        includePathConfig.extendsFrom(implementation);
        nativeLink.extendsFrom(implementation);
        nativeRuntime.extendsFrom(implementation);

        includePath = componentHeaderDirs.plus(includePathConfig);
        linkLibraries = nativeLink;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Provider<String> getBaseName() {
        return baseName;
    }

    @Override
    public FileCollection getCppSource() {
        return sourceFiles;
    }

    @Override
    public FileCollection getCompileIncludePath() {
        return includePath;
    }

    @Override
    public FileCollection getLinkLibraries() {
        return linkLibraries;
    }
}
