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

package org.gradle.language.cacheable;

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.changedetection.changes.IncrementalTaskInputsInternal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.incremental.DefaultSourceIncludesParser;
import org.gradle.language.nativeplatform.internal.incremental.DefaultSourceIncludesResolver;
import org.gradle.language.nativeplatform.internal.incremental.ResolvedInclude;
import org.gradle.language.nativeplatform.internal.incremental.SourceIncludesParser;
import org.gradle.language.nativeplatform.internal.incremental.SourceIncludesResolver;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.RegexBackedCSourceParser;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoverInputsForCompilation extends AbstractNativeTask {

    private final SourceIncludesParser includesParser;
    private File dependencyFile;

    @Inject
    public DiscoverInputsForCompilation(WorkerExecutor workerExecutor) {
        super(workerExecutor);
        this.includesParser = new DefaultSourceIncludesParser(new RegexBackedCSourceParser(), true);
    }

    @OutputFile
    public File getDependencyFile() {
        return dependencyFile;
    }

    public void setDependencyFile(File dependencyFile) {
        this.dependencyFile = dependencyFile;
    }

    @TaskAction
    public void discoverInputs(final IncrementalTaskInputs incrementalTaskInputs) throws IOException {
        final SourceIncludesResolver includesResolver = new DefaultSourceIncludesResolver(ImmutableList.<File>builder().add(
            // Default includes on my machine, found by running `clang++ -v foo.cpp -o /dev/null` with an empty file
            new File("/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/../include/c++/v1"),
            new File("Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/../lib/clang/8.1.0/include"),
            new File("/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include"),
            new File("/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.12.sdk/usr/include"),
            new File("/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.12.sdk/System/Library/Frameworks")
        ).addAll(getIncludeRoots()).build());
        final ConcurrentHashMap<String, Boolean> discoveredFiles = new ConcurrentHashMap<String, Boolean>();
        final IncrementalTaskInputsInternal inputs = (IncrementalTaskInputsInternal) incrementalTaskInputs;
        getSource().visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(final FileVisitDetails fileVisitDetails) {
                String name = fileVisitDetails.getName();
                if (!isSourceFile(name)) {
                    return;
                }
                File sourceFile = fileVisitDetails.getFile();
                discoverInputs(sourceFile, discoveredFiles, inputs, includesResolver);
            }
        });
        DependenciesFile.write(discoveredFiles.keySet(), getDependencyFile());
    }

    private void discoverInputs(File sourceFile, ConcurrentHashMap<String, Boolean> discoveredFiles, IncrementalTaskInputsInternal inputs, SourceIncludesResolver includesResolver) {
        IncludeDirectives includeDirectives = includesParser.parseIncludes(sourceFile);
        SourceIncludesResolver.ResolvedSourceIncludes resolvedSourceIncludes = includesResolver.resolveIncludes(sourceFile, includeDirectives);
        for (ResolvedInclude resolvedInclude : resolvedSourceIncludes.getResolvedIncludes()) {
            if (!resolvedInclude.isUnknown()) {
                File includeFile = resolvedInclude.getFile();
                Boolean previousValue = discoveredFiles.putIfAbsent(includeFile.getAbsolutePath(), Boolean.TRUE);
                if (previousValue == null) {
                    inputs.newInput(includeFile);
                    discoverInputs(includeFile, discoveredFiles, inputs, includesResolver);
                }
            }
        }
    }
}
