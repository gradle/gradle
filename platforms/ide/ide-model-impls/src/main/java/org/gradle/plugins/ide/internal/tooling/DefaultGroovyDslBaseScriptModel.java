/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.internal.classpath.ClassPath;
import org.gradle.tooling.model.dsl.GroovyDslBaseScriptModel;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.Serializable;
import java.util.List;

@NullMarked
class DefaultGroovyDslBaseScriptModel implements GroovyDslBaseScriptModel, Serializable {

    private final List<File> compileClassPath;
    private final List<String> implicitImports;

    public DefaultGroovyDslBaseScriptModel(ClassPath compileClassPath, List<String> implicitImports) {
        this.compileClassPath = compileClassPath.getAsFiles();
        this.implicitImports = implicitImports;
    }

    @Override
    public List<File> getCompileClassPath() {
        return compileClassPath;
    }

    @Override
    public List<String> getImplicitImports() {
        return implicitImports;
    }
}
