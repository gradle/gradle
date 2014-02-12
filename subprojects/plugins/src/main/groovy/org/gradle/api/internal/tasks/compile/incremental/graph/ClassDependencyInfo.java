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
import org.apache.commons.lang.mutable.MutableBoolean;
import org.gradle.api.internal.tasks.compile.incremental.ClassDependents;
import org.gradle.api.internal.tasks.compile.incremental.ClassNameProvider;
import org.gradle.api.internal.tasks.compile.incremental.DummySerializer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * by Szczepan Faber, created at: 1/15/14
 */
public class ClassDependencyInfo implements Serializable {

    private final Map<String, ClassDependents> dependents = new HashMap<String, ClassDependents>();

    public ClassDependencyInfo(File compiledClassesDir) {
        this(compiledClassesDir, "");
    }

    ClassDependencyInfo(File compiledClassesDir, String packagePrefix) {
        Iterator output = FileUtils.iterateFiles(compiledClassesDir, new String[]{"class"}, true);
        ClassNameProvider nameProvider = new ClassNameProvider(compiledClassesDir);
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
                        getOrCreateDependentMapping(dependency).addClass(className);
                    }
                }
                if (analysis.isDependentToAll()) {
                    getOrCreateDependentMapping(className).setDependentToAll();
                }
            } catch (IOException e) {
                throw new RuntimeException("Problems extracting class dependency from " + classFile, e);
            }
        }
    }

    private ClassDependents getOrCreateDependentMapping(String dependency) {
        ClassDependents d = dependents.get(dependency);
        if (d == null) {
            d = new ClassDependents();
            dependents.put(dependency, d);
        }
        return d;
    }

    public void writeTo(File outputFile) {
        ClassDependencyInfo target = this;
        DummySerializer.writeTargetTo(outputFile, target);
    }

    public static ClassDependencyInfo loadFrom(File inputFile) {
        return (ClassDependencyInfo) DummySerializer.readFrom(inputFile);
    }

    public Set<String> getActualDependents(String className) {
        Set<String> out = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        MutableBoolean isDependentToAll = new MutableBoolean(false);
        recurseDependents(visited, out, className, isDependentToAll);
        if (isDependentToAll.isTrue()) {
            return null;
        }
        out.remove(className);
        return out;
    }

    private void recurseDependents(Set<String> visited, Collection<String> accumulator, String className, MutableBoolean dependentToAll) {
        if (!visited.add(className)) {
            return;
        }
        ClassDependents out = dependents.get(className);
        if (out == null) {
            return;
        }
        if (out.isDependentToAll()) {
            dependentToAll.setValue(true);
            return;
        }
        for (String dependent : out.getDependentClasses()) {
            if (!dependent.contains("$") && !dependent.equals(className)) { //naive
                accumulator.add(dependent);
            }
            recurseDependents(visited, accumulator, dependent, dependentToAll);
        }
    }
}
