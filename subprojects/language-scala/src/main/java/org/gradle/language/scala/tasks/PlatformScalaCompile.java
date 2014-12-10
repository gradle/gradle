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

package org.gradle.language.scala.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.scala.AbstractScalaJavaJointCompileSpec;
import org.gradle.api.internal.tasks.scala.CleaningScalaCompiler;
import org.gradle.api.internal.tasks.scala.DefaultPlatformScalaJavaJointCompileSpec;
import org.gradle.api.internal.tasks.scala.PlatformScalaJavaJointCompileSpec;
import org.gradle.api.tasks.Nested;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.scala.internal.toolchain.ScalaToolChainInternal;
import org.gradle.language.scala.platform.ScalaPlatform;

import javax.inject.Inject;


/**
 * A platform-aware Scala compile task.
 */
@Incubating
public class PlatformScalaCompile extends AbstractScalaCompile<PlatformScalaJavaJointCompileSpec, PlatformScalaCompileOptions> {

    private ScalaPlatform platform;

    private final PlatformScalaCompileOptions scalaCompileOptions = new PlatformScalaCompileOptions();

    public ScalaPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(ScalaPlatform platform) {
        this.platform = platform;
    }

    @Inject
    protected ScalaToolChainInternal getToolChain() {
        throw new UnsupportedOperationException();
    }

    @Nested
    public PlatformScalaCompileOptions getScalaCompileOptions() {
        return scalaCompileOptions;
    }

    @Override
    protected AbstractScalaJavaJointCompileSpec<PlatformScalaCompileOptions> newSpec() {
        return new DefaultPlatformScalaJavaJointCompileSpec();
    }

    @Override
    protected Compiler<PlatformScalaJavaJointCompileSpec> getCompiler(PlatformScalaJavaJointCompileSpec spec) {
        Compiler<PlatformScalaJavaJointCompileSpec> scalaCompiler = getToolChain().select(getPlatform()).newCompiler(spec);
        return new CleaningScalaCompiler<PlatformScalaJavaJointCompileSpec>(scalaCompiler, getOutputs(), false);
    }

    @Override
    protected boolean useAnt() {
        return false;
    }
}
