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
import groovy.xml.XmlParser

class MavenPom {
    private final Node pom;
    final Map<String, MavenScope> scopes = [:]

    MavenPom(File pomFile) {
        if (pomFile.exists()){
            pom = new XmlParser().parse(pomFile)

            def scopesByDependency = ArrayListMultimap.create()

            pom.dependencies.dependency.each { dep ->
                def scope = createScope(dep.scope, 'compile')
                MavenDependency mavenDependency = createDependency(dep)
                if (mavenDependency.optional) {
                    scope.optionalDependencies[mavenDependency.getKey()] = mavenDependency
                } else {
                    scope.dependencies[mavenDependency.getKey()] = mavenDependency
                }
                scopesByDependency.put(mavenDependency.getKey(), scope.name)
            }

            pom.dependencyManagement.dependencies.dependency.each { dep ->
                def scope = createScope(dep.scope, 'no_scope')
                MavenDependency mavenDependency = createDependency(dep)
                scope.dependencyManagement[mavenDependency.getKey()] = mavenDependency
            }

            scopesByDependency.asMap().entrySet().findAll { it.value.size() > 1 }.each {
                throw new AssertionError("$it.key appeared in more than one scope: $it.value")
            }
        }
    }

    String getGroupId() {
        pom?.groupId[0]?.text()
    }

    String getArtifactId() {
        pom?.artifactId[0]?.text()
    }

    String getVersion() {
        pom?.version[0]?.text()
    }

    String getPackaging() {
        pom?.packaging[0]?.text()
    }

    String getName() {
        pom?.name[0]?.text()
    }

    String getDescription() {
        pom?.description[0]?.text()
    }

    String getUrl() {
        pom?.url[0]?.text()
    }

    String getInceptionYear() {
        pom?.inceptionYear[0]?.text()
    }

    Node getOrganization() {
        return pom?.organization[0]
    }

    NodeList getLicenses() {
        return pom?.licenses?.license
    }

    NodeList getDevelopers() {
        return pom?.developers?.developer
    }

    NodeList getContributors() {
        return pom?.contributors?.contributor
    }

    Node getScm() {
        return pom?.scm[0]
    }

    Node getIssueManagement() {
        return pom?.issueManagement[0]
    }

    Node getCiManagement() {
        return pom?.ciManagement[0]
    }

    Node getDistributionManagement() {
        return pom?.distributionManagement[0]
    }

    Node getDependencyManagement() {
        return pom?.dependencyManagement[0]
    }

    NodeList getMailingLists() {
        return pom?.mailingLists?.mailingList
    }

    Node getProperties() {
        return pom?.properties[0]
    }

    private MavenDependency createDependency(def dep) {
        def exclusions = []
        boolean optional = false
        if (dep.optional) {
            optional = "true"==dep.optional.text()
        }
        if (dep.exclusions) {
            dep.exclusions.exclusion.each { excl ->
                MavenDependencyExclusion exclusion = new MavenDependencyExclusion(
                    groupId: excl.groupId.text(),
                    artifactId: excl.artifactId.text(),
                )
                exclusions << exclusion
            }

        }
        new MavenDependency(
            groupId: dep.groupId.text(),
            artifactId: dep.artifactId.text(),
            version: dep.version.text(),
            classifier: dep.classifier ? dep.classifier.text() : null,
            type: dep.type ? dep.type.text() : null,
            exclusions: exclusions,
            optional: optional
        )
    }

    private MavenScope createScope(def scopeElement, String defaultScope) {
        def scopeName = scopeElement ? scopeElement.text() : defaultScope
        def scope = scopes[scopeName]
        if (!scope) {
            scope = new MavenScope(name: scopeName)
            scopes[scopeName] = scope
        }
        scope
    }

    void scope(String scopeName, @DelegatesTo(value=MavenScope, strategy=Closure.DELEGATE_FIRST) Closure<?> spec) {
        def scope = scopes[scopeName]
        if (scope) {
            spec.delegate = scope
            spec.resolveStrategy = Closure.DELEGATE_FIRST
            spec()
        } else {
            throw new AssertionError("Expected scope $scopeName but only found ${scopes.keySet()}")
        }
    }

    void hasNoScope(String scopeName) {
        assert scopes[scopeName] == null : "Didn't expect to find scope $scopeName"
    }
}
