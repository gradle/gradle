/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.javadoc.internal.JavadocGenerator;
import org.gradle.api.tasks.javadoc.internal.JavadocSpec;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

public class DefaultJavaToolChain implements JavaToolChainInternal {
    private final JavaCompilerFactory compilerFactory;
    private final ExecActionFactory execActionFactory;
    private final JavaVersion javaVersion;

    public DefaultJavaToolChain(JavaCompilerFactory compilerFactory, ExecActionFactory execActionFactory) {
        this.compilerFactory = compilerFactory;
        this.execActionFactory = execActionFactory;
        this.javaVersion = JavaVersion.current();
    }

    public String getName() {
        return String.format("JDK%s", javaVersion);
    }

    public String getDisplayName() {
        return String.format("JDK %s (%s)", javaVersion.getMajorVersion(), javaVersion);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public ToolProvider select(JavaPlatform targetPlatform) {
        // TODO:DAZ Remove all of the calls to this method with null platform
        if (targetPlatform != null && targetPlatform.getTargetCompatibility().compareTo(javaVersion) > 0) {
            return new UnavailableToolProvider(targetPlatform);
        }
        return new JavaToolProvider();
    }

    private class JavaToolProvider implements ToolProvider {
        public <T extends CompileSpec> Compiler<T> newCompiler(T spec) {
            if (spec instanceof JavaCompileSpec) {
                CompileOptions options = ((JavaCompileSpec) spec).getCompileOptions();
                @SuppressWarnings("unchecked") Compiler<T> compiler = (Compiler<T>) compilerFactory.create(options);
                return compiler;
            }
            if (spec instanceof JavadocSpec) {
                @SuppressWarnings("unchecked") Compiler<T> compiler = (Compiler<T>) new JavadocGenerator(execActionFactory);
                return compiler;
            }

            throw new IllegalArgumentException(String.format("Don't know how to compile using spec of type %s.", spec.getClass().getSimpleName()));
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private class UnavailableToolProvider implements ToolProvider {
        private final JavaPlatform targetPlatform;

        private UnavailableToolProvider(JavaPlatform targetPlatform) {
            this.targetPlatform = targetPlatform;
        }

        public <T extends CompileSpec> Compiler<T> newCompiler(T spec) {
            throw new IllegalArgumentException(getMessage());
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(getMessage());
        }

        private String getMessage() {
            return String.format("Could not target platform: '%s' using tool chain: '%s'.", targetPlatform.getDisplayName(), DefaultJavaToolChain.this.getDisplayName());
        }
    }
}
