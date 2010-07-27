/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.tasks.ide.eclipse;


import org.gradle.api.Project

public abstract class ProjectType {
    public static final Map BASE = [
            buildCommandNames: [],
            natureNames: [],
            sourceSets: { Project project -> [] as LinkedHashSet },
            plusConfigurations: {Project project -> [] as LinkedHashSet }]

    public static final Map JAVA = [
            buildCommandNames: ["org.eclipse.jdt.core.javabuilder"],
            natureNames: ["org.eclipse.jdt.core.javanature"],
            sourceSets: { Project project -> project.sourceSets },
            plusConfigurations: {Project project ->
                project.plugins.hasPlugin('java') ? [project.configurations.testRuntime] as LinkedHashSet : [] as LinkedHashSet }]

    public static final Map GROOVY = [
            buildCommandNames: ["org.eclipse.jdt.core.javabuilder"],
            natureNames: ["org.eclipse.jdt.groovy.core.groovyNature", "org.eclipse.jdt.core.javanature"],
            sourceSets: { Project project -> project.sourceSets },
            plusConfigurations: {Project project ->
                project.plugins.hasPlugin('java') ? [project.configurations.testRuntime] as LinkedHashSet : [] as LinkedHashSet }]

    public static final Map SCALA = [
            buildCommandNames: ["ch.epfl.lamp.sdt.core.scalabuilder"],
            natureNames: ["ch.epfl.lamp.sdt.core.scalanature", "org.eclipse.jdt.core.javanature"],
            sourceSets: { Project project -> project.sourceSets },
            plusConfigurations: {Project project ->
                project.plugins.hasPlugin('java') ? [project.configurations.testRuntime] as LinkedHashSet : [] as LinkedHashSet }]
}
