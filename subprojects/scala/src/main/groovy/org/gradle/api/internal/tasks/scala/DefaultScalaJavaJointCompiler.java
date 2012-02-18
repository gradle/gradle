/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.scala;

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

public class DefaultScalaJavaJointCompiler implements ScalaJavaJointCompiler {
    private final Compiler<ScalaCompileSpec> scalaCompiler;
    private final Compiler<JavaCompileSpec> javaCompiler;
    private ScalaJavaJointCompileSpec spec = new DefaultScalaJavaJointCompileSpec();

    public DefaultScalaJavaJointCompiler(Compiler<ScalaCompileSpec> scalaCompiler, JavaCompiler javaCompiler) {
        this.scalaCompiler = scalaCompiler;
        this.javaCompiler = javaCompiler;
    }

    public ScalaJavaJointCompileSpec getSpec() {
        return spec;
    }

    public void setSpec(ScalaJavaJointCompileSpec spec) {
        this.spec = spec;
    }

    public WorkResult execute() {
        scalaCompiler.setSpec(spec);
        scalaCompiler.execute();

        PatternFilterable patternSet = new PatternSet();
        patternSet.include("**/*.java");
        FileTree javaSource = getSpec().getSource().getAsFileTree().matching(patternSet);
        if (!javaSource.isEmpty()) {
            spec.setSource(javaSource);
            javaCompiler.setSpec(spec);
            javaCompiler.execute();
        }

        return new WorkResult() {
            public boolean getDidWork() {
                return true;
            }
        };
    }

}
