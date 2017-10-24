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

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppSharedLibrary;

import javax.inject.Inject;

public class DefaultCppLibrary extends DefaultCppComponent implements CppLibrary {
    private final ConfigurableFileCollection publicHeaders;
    private final FileCollection publicHeadersWithConvention;
    private final DefaultCppSharedLibrary debug;
    private final DefaultCppSharedLibrary release;
    private final Configuration api;

    @Inject
    public DefaultCppLibrary(String name, ProjectLayout projectLayout, ObjectFactory objectFactory, FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        publicHeaders = fileOperations.files();
        publicHeadersWithConvention = createDirView(publicHeaders, "src/" + name + "/public");
        debug = objectFactory.newInstance(DefaultCppSharedLibrary.class, name + "Debug", projectLayout, objectFactory, getBaseName(), true, getCppSource(), getAllHeaderDirs(), configurations, getImplementationDependencies());
        release = objectFactory.newInstance(DefaultCppSharedLibrary.class, name + "Release", projectLayout, objectFactory, getBaseName(), false, getCppSource(), getAllHeaderDirs(), configurations, getImplementationDependencies());

        api = configurations.maybeCreate(getNames().withSuffix("api"));
        api.setCanBeConsumed(false);
        api.setCanBeResolved(false);
        getImplementationDependencies().extendsFrom(api);
    }

    @Override
    public Configuration getApiDependencies() {
        return api;
    }

    @Override
    public ConfigurableFileCollection getPublicHeaders() {
        return publicHeaders;
    }

    @Override
    public void publicHeaders(Action<? super ConfigurableFileCollection> action) {
        action.execute(publicHeaders);
    }

    @Override
    public FileCollection getPublicHeaderDirs() {
        return publicHeadersWithConvention;
    }

    @Override
    public FileTree getPublicHeaderFiles() {
        return publicHeadersWithConvention.getAsFileTree().matching(new PatternSet().include("**/*.h"));
    }

    @Override
    public FileCollection getAllHeaderDirs() {
        return publicHeadersWithConvention.plus(super.getAllHeaderDirs());
    }

    @Override
    public CppSharedLibrary getDevelopmentBinary() {
        return debug;
    }

    @Override
    public CppSharedLibrary getDebugSharedLibrary() {
        return debug;
    }

    @Override
    public CppSharedLibrary getReleaseSharedLibrary() {
        return release;
    }
}
