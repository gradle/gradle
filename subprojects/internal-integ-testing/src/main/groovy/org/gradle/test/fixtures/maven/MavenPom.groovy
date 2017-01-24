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

import com.google.common.collect.ArrayListMultimap

class MavenPom {
    String groupId
    String artifactId
    String version
    String packaging
    String description
    final Map<String, MavenScope> scopes = [:]

    MavenPom(File pomFile) {
        if (pomFile.exists()){
            def pom = new XmlParser().parse(pomFile)

            groupId = pom.groupId[0]?.text()
            artifactId = pom.artifactId[0]?.text()
            version = pom.version[0]?.text()
            packaging = pom.packaging[0]?.text()
            description = pom.description[0]?.text()
            def scopesByDependency = ArrayListMultimap.create()

            pom.dependencies.dependency.each { dep ->
                def scopeElement = dep.scope
                def scopeName = scopeElement ? scopeElement.text() : "runtime"
                def scope = scopes[scopeName]
                def exclusions = []
                if (!scope) {
                    scope = new MavenScope()
                    scopes[scopeName] = scope
                }
                if (dep.exclusions){
                    dep.exclusions.exclusion.each { excl ->
                        MavenDependencyExclusion exclusion = new MavenDependencyExclusion(
                            groupId: excl.groupId.text(),
                            artifactId: excl.artifactId.text(),
                        )
                        exclusions << exclusion
                    }

                }
                MavenDependency mavenDependency = new MavenDependency(
                        groupId: dep.groupId.text(),
                        artifactId: dep.artifactId.text(),
                        version: dep.version.text(),
                        classifier: dep.classifier ? dep.classifier.text() : null,
                        type: dep.type ? dep.type.text() : null,
                        exclusions: exclusions,
                )
                def key = "${mavenDependency.groupId}:${mavenDependency.artifactId}:${mavenDependency.version}"
                key += mavenDependency.classifier ? ":${mavenDependency.classifier}" : ""
                scope.dependencies[key] = mavenDependency
                scopesByDependency.put(key, scopeName)
            }

            scopesByDependency.asMap().entrySet().findAll { it.value.size() > 1 }.each {
                throw new AssertionError("$it.key appeared in more than one scope: $it.value")
            }
        }
    }
}
