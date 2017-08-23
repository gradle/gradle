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

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;

import javax.inject.Inject;

public class DefaultCppApplication extends DefaultCppComponent implements CppApplication {
    private final DefaultCppExecutable debug;
    private final DefaultCppExecutable release;

    @Inject
    public DefaultCppApplication(String name, FileOperations fileOperations, ProviderFactory providerFactory) {
        super(name, fileOperations, providerFactory);
        debug = new DefaultCppExecutable(name + "Debug", getBaseName(), getCppSource(), getCompileIncludePath(), getLinkLibraries());
        release = new DefaultCppExecutable(name + "Release", getBaseName(), getCppSource(), getCompileIncludePath(), getLinkLibraries());
    }

    @Override
    public CppExecutable getDebugExecutable() {
        return debug;
    }

    @Override
    public CppExecutable getReleaseExecutable() {
        return release;
    }
}
