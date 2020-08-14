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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.javadoc.internal.JavadocCompilerAdapter;
import org.gradle.api.tasks.javadoc.internal.JavadocSpec;
import org.gradle.internal.logging.text.DiagnosticsVisitor;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.process.internal.ExecActionFactory;

public abstract class AbstractJavaToolChain implements JavaToolChainInternal {
    private final JavaCompilerFactory compilerFactory;
    private final ExecActionFactory execActionFactory;

    protected AbstractJavaToolChain(JavaCompilerFactory compilerFactory, ExecActionFactory execActionFactory) {
        this.compilerFactory = compilerFactory;
        this.execActionFactory = execActionFactory;
    }

    @Override
    public String getVersion() {
        // Currently, we only track the major version.
        return getJavaVersion().getMajorVersion();
    }

    @Override
    public String getName() {
        return "JDK" + getJavaVersion();
    }

    @Override
    public String getDisplayName() {
        return "JDK " + getJavaVersion().getMajorVersion() + " (" + getJavaVersion() + ")";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public ToolProvider select(JavaPlatform targetPlatform) {
        if (targetPlatform != null && targetPlatform.getTargetCompatibility().compareTo(getJavaVersion()) > 0) {
            return new UnavailableToolProvider(targetPlatform);
        }
        return new JavaToolProvider();
    }

    private class JavaToolProvider implements ToolProvider {
        @Override
        public <T extends CompileSpec> Compiler<T> newCompiler(Class<T> spec) {
            if (JavaCompileSpec.class.isAssignableFrom(spec)) {
                @SuppressWarnings({"unchecked", "cast"}) Compiler<T> compiler = (Compiler<T>) compilerFactory.create(spec);
                return compiler;
            }
            if (JavadocSpec.class.isAssignableFrom(spec)) {
                @SuppressWarnings("unchecked") Compiler<T> compiler = (Compiler<T>) new JavadocCompilerAdapter(execActionFactory);
                return compiler;
            }

            throw new IllegalArgumentException(String.format("Don't know how to compile using spec of type %s.", spec.getClass().getSimpleName()));
        }

        @Override
        public <T> T get(Class<T> toolType) {
            throw new IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()));
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void explain(DiagnosticsVisitor visitor) {
        }
    }

    private class UnavailableToolProvider implements ToolProvider {
        private final JavaPlatform targetPlatform;

        private UnavailableToolProvider(JavaPlatform targetPlatform) {
            this.targetPlatform = targetPlatform;
        }

        @Override
        public <T extends CompileSpec> Compiler<T> newCompiler(Class<T> spec) {
            throw new IllegalArgumentException(getMessage());
        }

        @Override
        public <T> T get(Class<T> toolType) {
            throw new IllegalArgumentException(getMessage());
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void explain(DiagnosticsVisitor visitor) {
            visitor.node(getMessage());
        }

        private String getMessage() {
            return String.format("Could not target platform: '%s' using tool chain: '%s'.", targetPlatform.getDisplayName(), AbstractJavaToolChain.this.getDisplayName());
        }
    }
}
