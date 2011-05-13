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
package org.gradle.api.publication.maven.internal

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import spock.lang.Specification

class DefaultMavenPomCustomizerTest extends Specification {
    DefaultMavenPomCustomizer pom = new DefaultMavenPomCustomizer()
    Model model = new Model()

    def "can set values via POM builder"() {
        when:
        pom {
            description "foo"
        }
        pom.execute(model)

        then:
        model.description == "foo"
    }

    def "can add new elements via POM builder"() {
        when:
        pom {
            dependencies {
                dependency {
                    groupId "org.foo"
                    artifactId "bar"
                    version "1.0"
                }
            }
        }
        pom.execute(model)

        then:
        model.dependencies.size() == 1
        def dep = model.dependencies[0]
        dep.groupId == "org.foo"
        dep.artifactId == "bar"
        dep.version == "1.0"
    }

    def "can change existing elements via whenConfigured()"() {
        def dependency = new Dependency()
        dependency.version == "1.0"
        model.addDependency(dependency)

        when:
        pom.whenConfigured { Model model ->
            model.dependencies[0].version = "2.0"
        }
        pom.execute(model)

        then:
        model.dependencies[0].version == "2.0"
    }

    def "can remove existing elements via whenConfigured()"() {
        def dependency = new Dependency()
        dependency.version == "1.0"
        model.addDependency(dependency)

        when:
        pom.whenConfigured { Model model ->
            model.dependencies.remove(0)
        }
        pom.execute(model)

        then:
        model.dependencies.size() == 0
    }

    def "POM is correctly rendered as XML"() {
        model.groupId = "com.foo"
        model.artifactId = "bar"
        model.version = "1.0"
        model.addDependency(dependency("dep_group", "dep_artifact", "2.0"))

        when:
        def xml = pom.execute(model)

        then:
        def project = new XmlSlurper().parseText(xml)
        project.groupId == "com.foo"
        project.artifactId == "bar"
        project.version == "1.0"

        def dep = project.dependencies.dependency[0]
        dep.groupId == "dep_group"
        dep.artifactId == "dep_artifact"
        dep.version == "2.0"
    }

    def "can customize POM on XML level"() {
        model.modelVersion = "4.0.0"

        when:
        pom.withXml { xmlProvider ->
            def xml = xmlProvider.asNode()
            xml.modelVersion[0].value = "5.0.0"
        }
        def xml = pom.execute(model)

        then:
        def project = new XmlSlurper().parseText(xml)
        project.modelVersion == "5.0.0"
    }

    private dependency(String groupId, String artifactId, String version) {
        def dep = new Dependency()
        dep.groupId = groupId
        dep.artifactId = artifactId
        dep.version = version
        dep
    }
}
