/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.graph;

import org.apache.commons.io.FileUtils;
import org.gradle.api.internal.tasks.compile.incremental.ClassDependents;
import org.gradle.api.internal.tasks.compile.incremental.ClassNameProvider;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ClassDependencyInfoExtractor {

    private File classesDir;

    public ClassDependencyInfoExtractor(File classesDir) {
        this.classesDir = classesDir;
    }

    public ClassDependencyInfo extractInfo(String packagePrefix) {
        Map<String, ClassDependents> dependents = new HashMap<String, ClassDependents>();
        Iterator output = FileUtils.iterateFiles(classesDir, new String[]{"class"}, true);
        ClassNameProvider nameProvider = new ClassNameProvider(classesDir);
        while (output.hasNext()) {
            File classFile = (File) output.next();
            String className = nameProvider.provideName(classFile);
            if (!className.startsWith(packagePrefix)) {
                continue;
            }
            try {
                ClassAnalysis analysis = new ClassDependenciesAnalyzer().getClassAnalysis(className, classFile);
                for (String dependency : analysis.getClassDependencies()) {
                    if (!dependency.equals(className) && dependency.startsWith(packagePrefix)) {
                        getOrCreateDependentMapping(dependents, dependency).addClass(className);
                    }
                }
                if (analysis.isDependentToAll()) {
                    getOrCreateDependentMapping(dependents, className).setDependentToAll();
                }
            } catch (IOException e) {
                throw new RuntimeException("Problems extracting class dependency from " + classFile, e);
            }
        }
        return new ClassDependencyInfo(dependents);
    }

    private ClassDependents getOrCreateDependentMapping(Map<String, ClassDependents> dependents, String dependency) {
        ClassDependents d = dependents.get(dependency);
        if (d == null) {
            d = new ClassDependents();
            dependents.put(dependency, d);
        }
        return d;
    }
}