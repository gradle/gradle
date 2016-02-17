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

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import org.gradle.api.AntBuilder;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Factory;
import org.gradle.language.base.internal.tasks.StaleClassCleaner;

import java.io.File;

public class AntDependsStaleClassCleaner extends StaleClassCleaner {

    private final Factory<AntBuilder> antBuilderFactory;
    private final CompileOptions compileOptions;
    private File dependencyCacheDir;

    public AntDependsStaleClassCleaner(Factory<AntBuilder> antBuilderFactory, CompileOptions compileOptions) {
        this.antBuilderFactory = antBuilderFactory;
        this.compileOptions = compileOptions;
    }

    public void setDependencyCacheDir(File dependencyCacheDir) {
        this.dependencyCacheDir = dependencyCacheDir;
    }

    @Override
    public void execute() {
        ImmutableMap.Builder<String, Object> options = ImmutableMap.builder();
        options.put("destDir", getDestinationDir());
        options.putAll(compileOptions.getDependOptions().optionMap());
        if (compileOptions.getDependOptions().isUseCache()) {
            options.put("cache", dependencyCacheDir);
        }

        final AntBuilder ant = antBuilderFactory.create();
        ant.getProject().addTaskDefinition("gradleDepend", AntDepend.class);
        ant.invokeMethod("gradleDepend", new Object[]{options.build(), new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
            public void doCall(Object ignore) {
                getSource().addToAntBuilder(ant, "src", FileCollection.AntType.MatchingTask);
            }
        }});
    }

}
