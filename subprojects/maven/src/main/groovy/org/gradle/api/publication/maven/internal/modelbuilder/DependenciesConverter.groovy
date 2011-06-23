/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.publication.maven.internal.modelbuilder

import org.gradle.api.publication.maven.MavenDependency
import org.gradle.api.Project
import org.gradle.api.publication.maven.internal.model.DefaultMavenDependency
import org.gradle.api.publication.maven.MavenScope
import org.gradle.api.artifacts.ExternalDependency

/**
 * @author: Szczepan Faber, created at: 6/21/11
 */
class DependenciesConverter {
    List<MavenDependency> convert(Project project) {
        //TODO SF:
        //First fundamental question is should we reuse Conf2ScopeMappingContainer / PomDependenciesConverter ? How far?

        //should project dependencies be transformed into entries in the pom?
        //how to approach the case when the ExternalDependency has multiple artifcts? Don't know when it happens, though
        //we could check if war plugin was applied and deal with providedCompile and providedRuntime?
        //should we make sure that there are no duplicate entries e.g. the same library in both compile scope and test scope

        //0. I absolutely hate it but the goal today is not to make the DSL perfect but to have working pre-population of the model
        //1. It suffers from the fundamental convention mapping issue - non mutable collections
        //2. It is hard to reconfigure by the user (Imagine the user typing all this code what I did below if he needs to put a dependency from a different configuration)
        //3. I don't want to pass Configurations to the maven model. We went down that path with ide plugins and it bites us hard. We need the DependencySet!
        def out = new LinkedList()
        project.configurations['compile'].getDependencies(ExternalDependency).each {
            out << new DefaultMavenDependency(it, MavenScope.COMPILE);
        }

        project.configurations['testCompile'].getDependencies(ExternalDependency).each {
            out << new DefaultMavenDependency(it, MavenScope.TEST);
        }

        project.configurations['runtime'].getDependencies(ExternalDependency).each {
            out << new DefaultMavenDependency(it, MavenScope.RUNTIME);
        }

        project.configurations['testRuntime'].getDependencies(ExternalDependency).each {
            out << new DefaultMavenDependency(it, MavenScope.TEST);
        }
        return out
    }
}