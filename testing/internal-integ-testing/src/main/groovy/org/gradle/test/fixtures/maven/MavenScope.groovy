/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.test.fixtures.maven

import org.apache.commons.lang.StringUtils

class MavenScope {
    String name
    Map<String, MavenDependency> optionalDependencies = [:]
    Map<String, MavenDependency> dependencies = [:]
    Map<String, MavenDependency> dependencyManagement = [:]

    void assertNoDependencies() {
        assert dependencies.isEmpty()
    }

    void assertNoDependencyManagement() {
        assert dependencyManagement.isEmpty()
    }

    void assertDependsOn(String... expected) {
        assertDependencies(dependencies, expected)
    }

    private void assertDependencies(Map<String, MavenDependency> dependencies, String... expected) {
        assert dependencies.size() == expected.length
        expected.each {
            String key = StringUtils.substringBefore(it, "@")
            def dependency = expectDependency(key, dependencies)

            String type = null
            if (it != key) {
                type = StringUtils.substringAfter(it, "@")
            }
            assert dependency.hasType(type)
        }
    }

    void assertOptionalDependencies(String... expected) {
        assertDependencies(optionalDependencies, expected)
    }

    void assertDependencyManagement(String... expected) {
        assert dependencyManagement.size() == expected.length
        expected.each {
            String key = StringUtils.substringBefore(it, "@")
            def dependency = expectDependencyManagement(key)

            String type = null
            if (it != key) {
                type = StringUtils.substringAfter(it, "@")
            }
            assert dependency.hasType(type)
        }
    }

    boolean hasDependencyExclusion(String dependency, MavenDependencyExclusion exclusion) {
        def dep = expectDependency(dependency)
        dep.exclusions.contains(exclusion)
    }

    MavenDependency expectDependency(String key, Map<String, MavenDependency> lookup = dependencies) {
        final dependency = lookup[key]
        if (dependency == null) {
            throw new AssertionError("Could not find expected dependency $key. Actual: ${lookup.values()}")
        }
        return dependency
    }

    MavenDependency expectDependencyManagement(String key) {
        final dependency = dependencyManagement[key]
        if (dependency == null) {
            throw new AssertionError("Could not find expected dependencyManagement $key. Actual: ${dependencyManagement.values()}")
        }
        return dependency
    }

    @Override
    String toString() {
        "$dependencies"
    }
}
