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

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.Linkage;

import javax.inject.Inject;

public class DefaultCppApplication extends DefaultCppComponent implements CppApplication {
    private final DefaultCppExecutable debugShared;
    private final DefaultCppExecutable releaseShared;
    private final DefaultCppExecutable debugStatic;
    private final DefaultCppExecutable releaseStatic;

    @Inject
    public DefaultCppApplication(String name, ProjectLayout projectLayout, ObjectFactory objectFactory, FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        debugShared = objectFactory.newInstance(DefaultCppExecutable.class, name + "DebugShared", projectLayout, objectFactory, getBaseName(), true, getCppSource(), getPrivateHeaderDirs(), configurations, getImplementationDependencies(), Linkage.SHARED);
        releaseShared = objectFactory.newInstance(DefaultCppExecutable.class, name + "ReleaseShared", projectLayout, objectFactory, getBaseName(), false, getCppSource(), getPrivateHeaderDirs(), configurations, getImplementationDependencies(), Linkage.SHARED);
        debugStatic = objectFactory.newInstance(DefaultCppExecutable.class, name + "DebugStatic", projectLayout, objectFactory, getBaseName(), true, getCppSource(), getPrivateHeaderDirs(), configurations, getImplementationDependencies(), Linkage.STATIC);
        releaseStatic = objectFactory.newInstance(DefaultCppExecutable.class, name + "ReleaseStatic", projectLayout, objectFactory, getBaseName(), false, getCppSource(), getPrivateHeaderDirs(), configurations, getImplementationDependencies(), Linkage.STATIC);
    }

    @Override
    public CppExecutable getDevelopmentBinary() {
        return debugShared;
    }

    @Override
    public CppExecutable getDebugExecutable() {
        return debugShared;
    }

    @Override
    public CppExecutable getReleaseExecutable() {
        return releaseShared;
    }

    @Override
    public CppExecutable getDebugStaticExecutable() {
        return debugStatic;
    }

    @Override
    public CppExecutable getReleaseStaticExecutable() {
        return releaseStatic;
    }
}
