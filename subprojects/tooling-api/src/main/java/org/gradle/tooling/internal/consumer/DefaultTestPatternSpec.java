/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.TestPatternSpec;
import org.gradle.tooling.internal.protocol.test.InternalTestPatternSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultTestPatternSpec implements TestPatternSpec, InternalTestPatternSpec {

    private final String taskPath;
    private final List<String> classes;
    private final Map<String, List<String>> methods;
    private final List<String> packages;
    private final List<String> patterns;

    DefaultTestPatternSpec(String taskPath) {
        this(taskPath, new ArrayList<String>(), new LinkedHashMap<String, List<String>>(), new ArrayList<String>(), new ArrayList<String>());
    }

    public DefaultTestPatternSpec(String taskPath, List<String> classes, Map<String, List<String>> methods,  List<String> packages, List<String> patterns) {
        this.taskPath = taskPath;
        this.packages = packages;
        this.classes = classes;
        this.methods = methods;
        this.patterns = patterns;
    }

    @Override
    public TestPatternSpec includePackage(String pkg) {
        return includePackages(Collections.singletonList(pkg));
    }

    @Override
    public TestPatternSpec includePackages(Collection<String> packages) {
        this.packages.addAll(packages);
        return this;
    }

    @Override
    public TestPatternSpec includeClass(String cls) {
        return includeClasses(Collections.singletonList(cls));
    }

    @Override
    public TestPatternSpec includeClasses(Collection<String> classes) {
        this.classes.addAll(classes);
        return this;
    }

    @Override
    public TestPatternSpec includeMethod(String cls, String method) {
        return includeMethods(cls, Collections.singletonList(method));
    }

    @Override
    public TestPatternSpec includeMethods(String clazz, Collection<String> newMethods) {
        List<String> methods = this.methods.get(clazz);
        if (methods == null) {
            methods = new ArrayList<String>(newMethods.size());
            this.methods.put(clazz, methods);
        }
        methods.addAll(newMethods);
        return this;
    }

    @Override
    public TestPatternSpec includePattern(String pattern) {
        return includePatterns(Collections.singletonList(pattern));
    }

    @Override
    public TestPatternSpec includePatterns(Collection<String> patterns) {
        this.patterns.addAll(patterns);
        return this;
    }

    @Override
    public String getTaskPath() {
        return taskPath;
    }

    @Override
    public List<String> getPackages() {
        return packages;
    }

    @Override
    public List<String> getClasses() {
        return classes;
    }

    @Override
    public Map<String, List<String>> getMethods() {
        return methods;
    }

    @Override
    public List<String> getPatterns() {
        return patterns;
    }
}
