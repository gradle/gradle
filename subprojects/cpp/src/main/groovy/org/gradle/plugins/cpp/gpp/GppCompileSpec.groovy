/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.cpp.gpp
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.api.tasks.TaskDependency
import org.gradle.plugins.binaries.model.NativeComponent
import org.gradle.plugins.binaries.model.internal.CompileTaskAware
import org.gradle.plugins.cpp.CppCompile

class GppCompileSpec extends DefaultCppCompileSpec implements CompileTaskAware {
    NativeComponent nativeComponent

    private CppCompile task
    List<Closure> settings = []

    private final Compiler<? super GppCompileSpec> compiler
    private final ProjectInternal project

    GppCompileSpec(NativeComponent nativeComponent, Compiler<? super GppCompileSpec> compiler, ProjectInternal project) {
        this.nativeComponent = nativeComponent
        this.compiler = compiler
        this.project = project
    }

    // TODO:DAZ Remove this
    void configure(CppCompile task) {
        this.task = task
        task.spec = this
        task.compiler = compiler
    }

    String getName() {
        nativeComponent.name
    }

    TaskDependency getBuildDependencies() {
        return new DefaultTaskDependency().add(task)
    }

    File getWorkDir() {
        project.file "$project.buildDir/compileWork/$name"
    }

    void setting(Closure closure) {
        settings << closure
    }

    void args(Object... args) {
        setting {
            it.args args
        }
    }
}