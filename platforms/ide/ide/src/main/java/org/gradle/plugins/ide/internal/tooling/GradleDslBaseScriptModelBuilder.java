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
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.stream.Stream;

@NullMarked
public class GradleDslBaseScriptModelBuilder implements BuildScopeModelBuilder {

    private static final String MODEL_NAME = GradleDslBaseScriptModel.class.getName();

    @Override
    public boolean canBuild(String modelName) {
        return MODEL_NAME.equals(modelName);
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
        // TODO: We should allow the ModuleRegistry to generate this list instead of
        // controlling it manually. We should have a separate project for our script templates,
        // where its runtime classpath contains only dependencies we want, so when loading the
        // template module from the registry we get this list auto-generated for us.
        Stream<String> moduleNames = Stream.of(
            "gradle-base-services",
            "gradle-base-services-groovy",
            "gradle-core-api",
            "gradle-kotlin-dsl",
            "gradle-kotlin-dsl-shared-runtime",
            "gradle-kotlin-dsl-tooling-models",
            "kotlin-script-runtime"
        );

        return moduleNames.map(name -> moduleRegistry.getModule(name).getImplementationClasspath())
            .reduce(ClassPath.EMPTY, ClassPath::plus, ClassPath::plus);
    }

}
