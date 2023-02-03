/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import groovy.lang.Script;
import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.scripts.CompileScriptBuildOperationType;

import java.io.File;

public class BuildOperationBackedScriptCompilationHandler implements ScriptCompilationHandler {

    public static final String GROOVY_LANGUAGE = "GROOVY";

    private static final CompileScriptBuildOperationType.Result RESULT = new CompileScriptBuildOperationType.Result() {
    };

    private final DefaultScriptCompilationHandler delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationBackedScriptCompilationHandler(DefaultScriptCompilationHandler delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void compileToDir(final ScriptSource source, final ClassLoader classLoader, final File classesDir, final File metadataDir, final CompileOperation<?> transformer, final Class<? extends Script> scriptBaseClass, final Action<? super ClassNode> verifier) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                delegate.compileToDir(source, classLoader, classesDir, metadataDir, transformer, scriptBaseClass, verifier);
                context.setResult(RESULT);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String stage = transformer.getStage();
                String name = "Compile " + source.getShortDisplayName() + " (" + stage + ")";
                return BuildOperationDescriptor.displayName(name)
                    .name(name)
                    .details(new Details(stage));
            }
        });
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> loadFromDir(ScriptSource source, HashCode sourceHashCode, ClassLoaderScope targetScope, ClassPath scriptClassPath, File metadataCacheDir, CompileOperation<M> transformer, Class<T> scriptBaseClass) {
        return delegate.loadFromDir(source, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, transformer, scriptBaseClass);
    }

    private static class Details implements CompileScriptBuildOperationType.Details {

        private final String stage;

        Details(String stage) {
            this.stage = stage;
        }

        @Override
        public String getLanguage() {
            return GROOVY_LANGUAGE;
        }

        @Override
        public String getStage() {
            return stage;
        }
    }
}
