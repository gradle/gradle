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
import org.gradle.tooling.model.dsl.KotlinDslBaseScriptModel;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

@NullMarked
class DefaultKotlinDslBaseScriptModel implements KotlinDslBaseScriptModel, Serializable {

    private static final List<String> TEMPLATE_CLASS_NAMES = Arrays.asList(
        "org.gradle.kotlin.dsl.KotlinGradleScriptTemplate",
        "org.gradle.kotlin.dsl.KotlinSettingsScriptTemplate",
        "org.gradle.kotlin.dsl.KotlinProjectScriptTemplate"
    );

    private final List<File> scriptTemplatesClassPath;
    private final List<File> compileClassPath;
    private final List<String> implicitImports;

    public DefaultKotlinDslBaseScriptModel(ClassPath scriptTemplatesClassPath, ClassPath compileClassPath, List<String> implicitImports) {
        this.scriptTemplatesClassPath = scriptTemplatesClassPath.getAsFiles();
        this.compileClassPath = compileClassPath.getAsFiles();
        this.implicitImports = implicitImports;
    }

    @Override
    public List<File> getScriptTemplatesClassPath() {
        return scriptTemplatesClassPath;
    }

    @Override
    public List<File> getCompileClassPath() {
        return compileClassPath;
    }

    @Override
    public List<String> getImplicitImports() {
        return implicitImports;
    }

    @Override
    public List<String> getTemplateClassNames() {
        return TEMPLATE_CLASS_NAMES;
    }

}
