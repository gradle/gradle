/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.AntBuilder
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.internal.Factory
import org.gradle.language.jvm.internal.StaleClassCleaner

class AntDependsStaleClassCleaner extends StaleClassCleaner {
    private final Factory<AntBuilder> antBuilderFactory
    private final CompileOptions compileOptions

    File dependencyCacheDir

    AntDependsStaleClassCleaner(Factory<AntBuilder> antBuilderFactory, CompileOptions compileOptions) {
        this.antBuilderFactory = antBuilderFactory;
        this.compileOptions = compileOptions;
    }

    void execute() {
        def dependArgs = [destDir: destinationDir]
        def dependOptions = dependArgs + compileOptions.dependOptions.optionMap()
        if (compileOptions.dependOptions.useCache) {
            dependOptions['cache'] = dependencyCacheDir
        }

        def ant = antBuilderFactory.create()
        ant.project.addTaskDefinition('gradleDepend', AntDepend)
        ant.gradleDepend(dependOptions) {
            source.addToAntBuilder(ant, 'src', FileCollection.AntType.MatchingTask)
        }
    }
}
