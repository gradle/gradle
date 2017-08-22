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
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class DefaultCppComponent extends DefaultNativeComponent implements CppComponent {
    private final FileCollection cppSource;
    private final FileOperations fileOperations;
    private final ConfigurableFileCollection privateHeaders;
    private final FileCollection privateHeadersWithConvention;
    private final ConfigurableFileCollection compileIncludePath;
    private final PropertyState<String> baseName;

    @Inject
    public DefaultCppComponent(FileOperations fileOperations, ProviderFactory providerFactory) {
        super(fileOperations);
        this.fileOperations = fileOperations;
        cppSource = createSourceView("src/main/cpp", Arrays.asList("cpp", "c++"));
        privateHeaders = fileOperations.files();
        privateHeadersWithConvention = createDirView(privateHeaders, "src/main/headers");
        compileIncludePath = fileOperations.files();
        compileIncludePath.from(privateHeadersWithConvention);
        baseName = providerFactory.property(String.class);
    }

    protected FileCollection createDirView(final ConfigurableFileCollection dirs, final String conventionLocation) {
        return fileOperations.files(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                if (dirs.getFrom().isEmpty()) {
                    return fileOperations.files(conventionLocation);
                }
                return dirs;
            }
        });
    }

    @Override
    public PropertyState<String> getBaseName() {
        return baseName;
    }

    @Override
    public FileCollection getCppSource() {
        return cppSource;
    }

    @Override
    public ConfigurableFileCollection getPrivateHeaders() {
        return privateHeaders;
    }

    @Override
    public void privateHeaders(Action<? super ConfigurableFileCollection> action) {
        action.execute(privateHeaders);
    }

    @Override
    public FileCollection getPrivateHeaderDirs() {
        return privateHeadersWithConvention;
    }

    @Override
    public ConfigurableFileCollection getCompileIncludePath() {
        return compileIncludePath;
    }

    @Override
    public FileTree getHeaderFiles() {
        return getAllHeaderDirs().getAsFileTree().matching(new PatternSet().include("**/*.h"));
    }

    protected FileCollection getAllHeaderDirs() {
        return privateHeadersWithConvention;
    }
}
