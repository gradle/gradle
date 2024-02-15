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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultJavaCompileSpecFactory extends AbstractJavaCompileSpecFactory<DefaultJavaCompileSpec> {
    public DefaultJavaCompileSpecFactory(CompileOptions compileOptions, @Nullable JavaInstallationMetadata toolchain) {
        super(compileOptions, toolchain);
    }

    @Override
    protected DefaultJavaCompileSpec getCommandLineSpec(File executable) {
        return new DefaultCommandLineJavaSpec(executable);
    }

    @Override
    protected DefaultJavaCompileSpec getForkingSpec(File javaHome, int javaLanguageVersion) {
        return new DefaultForkingJavaCompileSpec(javaHome, javaLanguageVersion);
    }

    @Override
    protected DefaultJavaCompileSpec getDefaultSpec() {
        return new DefaultJavaCompileSpec();
    }

    private static class DefaultCommandLineJavaSpec extends DefaultJavaCompileSpec implements CommandLineJavaCompileSpec {
        private final File executable;

        private DefaultCommandLineJavaSpec(File executable) {
            this.executable = executable;
        }

        @Override
        public File getExecutable() {
            return executable;
        }
    }

    private static class DefaultForkingJavaCompileSpec extends DefaultJavaCompileSpec implements ForkingJavaCompileSpec {
        private final File javaHome;
        private final int javaLanguageVersion;

        private DefaultForkingJavaCompileSpec(File javaHome, int javaLanguageVersion) {
            this.javaHome = javaHome;
            this.javaLanguageVersion = javaLanguageVersion;
        }

        @Override
        public File getJavaHome() {
            return javaHome;
        }

        @Override
        public int getJavaLanguageVersion() {
            return javaLanguageVersion;
        }
    }
}
