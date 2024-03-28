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

public class DefaultGroovyJavaJointCompileSpecFactory extends AbstractJavaCompileSpecFactory<DefaultGroovyJavaJointCompileSpec> {
    public DefaultGroovyJavaJointCompileSpecFactory(CompileOptions compileOptions, @Nullable JavaInstallationMetadata javaInstallationMetadata) {
        super(compileOptions, javaInstallationMetadata);
    }

    @Override
    protected DefaultGroovyJavaJointCompileSpec getCommandLineSpec(File executable) {
        return new DefaultCommandLineGroovyJavaJointCompileSpec(executable);
    }

    @Override
    protected DefaultGroovyJavaJointCompileSpec getForkingSpec(File javaHome, int javaLanguageVersion) {
        return new DefaultForkingGroovyJavaJointCompileSpec(javaHome, javaLanguageVersion);
    }

    @Override
    protected DefaultGroovyJavaJointCompileSpec getDefaultSpec() {
        return new DefaultGroovyJavaJointCompileSpec();
    }

    private static class DefaultCommandLineGroovyJavaJointCompileSpec extends DefaultGroovyJavaJointCompileSpec implements CommandLineJavaCompileSpec {
        private final File executable;

        private DefaultCommandLineGroovyJavaJointCompileSpec(File executable) {
            this.executable = executable;
        }

        @Override
        public File getExecutable() {
            return executable;
        }
    }

    private static class DefaultForkingGroovyJavaJointCompileSpec extends DefaultGroovyJavaJointCompileSpec implements ForkingJavaCompileSpec {
        private final File javaHome;
        private final int javaLanguageVersion;

        private DefaultForkingGroovyJavaJointCompileSpec(File javaHome, int javaLanguageVersion) {
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
