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

package org.gradle.api.internal.tasks.scala;

import org.gradle.api.internal.tasks.compile.AbstractJavaCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.ForkingJavaCompileSpec;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;

import javax.annotation.Nullable;

public class DefaultScalaJavaJointCompileSpecFactory extends AbstractJavaCompileSpecFactory<DefaultScalaJavaJointCompileSpec> {
    public DefaultScalaJavaJointCompileSpecFactory(CompileOptions compileOptions, @Nullable JavaInstallationMetadata javaInstallationMetadata) {
        super(compileOptions, javaInstallationMetadata);
    }

    @Override
    protected DefaultScalaJavaJointCompileSpec getCommandLineSpec() {
        return new DefaultCommandLineScalaJavaJointCompileSpec();
    }

    @Override
    protected DefaultScalaJavaJointCompileSpec getForkingSpec() {
        return new DefaultForkingScalaJavaJointCompileSpec();
    }

    @Override
    protected DefaultScalaJavaJointCompileSpec getDefaultSpec() {
        return new DefaultScalaJavaJointCompileSpec();
    }

    private static class DefaultCommandLineScalaJavaJointCompileSpec extends DefaultScalaJavaJointCompileSpec implements CommandLineJavaCompileSpec {
    }

    private static class DefaultForkingScalaJavaJointCompileSpec extends DefaultScalaJavaJointCompileSpec implements ForkingJavaCompileSpec {
    }
}
