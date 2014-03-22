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

package org.gradle.api.internal.tasks.compile.incremental.deps;

import org.apache.commons.io.FileUtils;
import org.gradle.api.internal.tasks.compile.incremental.ClassDependents;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ClassDependencyInfoExtractor {

    private final ClassDependenciesAnalyzer analyzer;

    public ClassDependencyInfoExtractor(ClassDependenciesAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public ClassDependencyInfo extractInfo(File compiledClassesDir, String packagePrefix) {
        Map<String, ClassDependents> dependents = new HashMap<String, ClassDependents>();
        Iterator output = FileUtils.iterateFiles(compiledClassesDir, new String[]{"class"}, true);
        OutputToNameConverter nameProvider = new OutputToNameConverter(compiledClassesDir);
        while (output.hasNext()) {
            File classFile = (File) output.next();
            String className = nameProvider.getClassName(classFile);
            if (!className.startsWith(packagePrefix)) {
                continue;
            }
            try {
                ClassAnalysis analysis = analyzer.getClassAnalysis(className, classFile);
                for (String dependency : analysis.getClassDependencies()) {
                    if (!dependency.equals(className) && dependency.startsWith(packagePrefix)) {
                        populate(dependents, dependency, className);
                    }
                }
                if (analysis.isDependencyToAll()) {
                    dependents.put(className, ClassDependents.dependencyToAll());
                }
            } catch (IOException e) {
                throw new RuntimeException("Problems extracting class dependency from " + classFile, e);
            }
        }
        return new ClassDependencyInfo((Map) dependents);
    }

    private ClassDependents populate(Map<String, ClassDependents> dependents, String dependency, String className) {
        ClassDependents d = dependents.get(dependency);
        if (d == null) {
            //init dependents
            d = ClassDependents.emptyDependents();
            dependents.put(dependency, d);
        } else if (d.isDependencyToAll()) {
            //don't add class if it is a dependency to all
            return d;
        }
        return d.addClass(className);
    }
}