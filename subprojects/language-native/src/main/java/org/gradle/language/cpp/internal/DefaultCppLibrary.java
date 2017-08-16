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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.cpp.CppLibrary;

import javax.inject.Inject;

public class DefaultCppLibrary extends DefaultCppComponent implements CppLibrary {
    private final ConfigurableFileCollection publicHeaders;
    private final FileCollection publicHeadersWithConvention;

    @Inject
    public DefaultCppLibrary(FileOperations fileOperations, ProviderFactory providerFactory) {
        super(fileOperations, providerFactory);
        publicHeaders = fileOperations.files();
        publicHeadersWithConvention = createDirView(publicHeaders, "src/main/public");
        getCompileIncludePath().setFrom(publicHeadersWithConvention, getPrivateHeaderDirs());
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
    protected FileCollection getAllHeaderDirs() {
        return publicHeadersWithConvention.plus(super.getAllHeaderDirs());
    }
}
