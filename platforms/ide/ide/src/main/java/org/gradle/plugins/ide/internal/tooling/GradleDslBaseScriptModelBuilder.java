/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.GradleApiImplicitImportsProvider;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.classpath.GradleApiClasspathProvider;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel;
import org.gradle.tooling.model.dsl.GroovyDslBaseScriptModel;
import org.gradle.tooling.model.dsl.KotlinDslBaseScriptModel;
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@NullMarked
public class GradleDslBaseScriptModelBuilder implements BuildScopeModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return "org.gradle.tooling.model.dsl.GradleDslBaseScriptModel".equals(modelName);
    }

    @Override
    public Object create(BuildState target) {
        GradleInternal gradle = target.getMutableModel();
        ModuleRegistry moduleRegistry = gradle.getServices().get(ModuleRegistry.class);
        GradleApiImplicitImportsProvider implicitImports = gradle.getServices().get(GradleApiImplicitImportsProvider.class);
        GradleApiClasspathProvider apiClasspathProvider = gradle.getServices().get(GradleApiClasspathProvider.class);

        DefaultKotlinDslBaseScriptModel kotlinDslBaseScriptModel = new DefaultKotlinDslBaseScriptModel(
            getKotlinScriptTemplatesClassPath(moduleRegistry),
            apiClasspathProvider.getGradleKotlinDslApi(),
            implicitImports.getKotlinDslImplicitImports()
        );

        DefaultGroovyDslBaseScriptModel groovyDslBaseScriptModel = new DefaultGroovyDslBaseScriptModel(
            apiClasspathProvider.getGradleApi(),
            implicitImports.getGroovyDslImplicitImports()
        );

        return new DefaultGradleDslBaseScriptModel(
            groovyDslBaseScriptModel,
            kotlinDslBaseScriptModel
        );
    }

    private static ClassPath getKotlinScriptTemplatesClassPath(ModuleRegistry moduleRegistry) {
        return Stream.of("gradle-core")
            .map(moduleRegistry::getModule)
            .flatMap(it -> it.getAllRequiredModules().stream())
            .flatMap(it -> it.getClasspath().getAsFiles().stream())
            .filter(GradleDslBaseScriptModelBuilder::isNeededOnScriptTemplateClassPath)
            .sorted()
            .reduce(ClassPath.EMPTY, (classPath, file) -> classPath.plus(Collections.singleton(file)), ClassPath::plus);
    }

    private static boolean isNeededOnScriptTemplateClassPath(File file) {
        String name = file.getName();
        if (!name.endsWith(".jar")) {
            return false;
        }
        return name.startsWith("gradle-kotlin-dsl-") || name.startsWith("gradle-core-api-") || name.startsWith("kotlin-script-runtime-");
    }
}

@NullMarked
class DefaultGradleDslBaseScriptModel implements GradleDslBaseScriptModel, Serializable {

    private final GroovyDslBaseScriptModel groovyDslBaseScriptModel;

    private final KotlinDslBaseScriptModel kotlinDslBaseScriptModel;

    public DefaultGradleDslBaseScriptModel(GroovyDslBaseScriptModel groovyDslBaseScriptModel, KotlinDslBaseScriptModel kotlinDslBaseScriptModel) {
        this.groovyDslBaseScriptModel = groovyDslBaseScriptModel;
        this.kotlinDslBaseScriptModel = kotlinDslBaseScriptModel;
    }

    @Override
    public GroovyDslBaseScriptModel getGroovyDslBaseScriptModel() {
        return groovyDslBaseScriptModel;
    }

    @Override
    public KotlinDslBaseScriptModel getKotlinDslBaseScriptModel() {
        return kotlinDslBaseScriptModel;
    }

}

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
