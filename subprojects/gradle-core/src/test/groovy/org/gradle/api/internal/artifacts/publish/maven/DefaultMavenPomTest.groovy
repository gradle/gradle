/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish.maven

import spock.lang.*
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer
import org.apache.maven.project.MavenProject
import org.gradle.api.artifacts.Configuration
import org.apache.maven.model.Dependency
import org.gradle.api.artifacts.maven.MavenPomListener
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.artifacts.maven.XmlProvider

class DefaultMavenPomTest extends Specification {
    static final String EXPECTED_PACKAGING = "something";
    static final String EXPECTED_GROUP_ID = "someGroup";
    static final String EXPECTED_ARTIFACT_ID = "artifactId";
    static final String EXPECTED_VERSION = "version";

    Conf2ScopeMappingContainer conf2ScopeMappingContainerMock = new DefaultConf2ScopeMappingContainer()
    PomDependenciesConverter pomDependenciesConverterStub = Mock(PomDependenciesConverter)
    DefaultMavenPom mavenPom = new DefaultMavenPom(conf2ScopeMappingContainerMock, pomDependenciesConverterStub, new MavenProject())

    void setup() {
        mavenPom.packaging = EXPECTED_PACKAGING
        mavenPom.groupId = EXPECTED_GROUP_ID
        mavenPom.artifactId = EXPECTED_ARTIFACT_ID
        mavenPom.version = EXPECTED_VERSION
    }

    def init() {
        expect:
        conf2ScopeMappingContainerMock.is(mavenPom.getScopeMappings())
        mavenPom.mavenProject.modelVersion == "4.0.0"
        mavenPom.packaging == EXPECTED_PACKAGING
        mavenPom.groupId == EXPECTED_GROUP_ID
        mavenPom.artifactId == EXPECTED_ARTIFACT_ID
        mavenPom.version == EXPECTED_VERSION
    }

    def addDependencies() {
        setup:
        Set configurations = [Mock(Configuration)]
        List generatedDependencies = [new Dependency()]
        List manuallyAddedDependencies = [new Dependency()]
        pomDependenciesConverterStub.convert(conf2ScopeMappingContainerMock, configurations) >> generatedDependencies

        when:
        mavenPom.dependencies = manuallyAddedDependencies.clone()
        mavenPom.addDependencies(configurations);

        then:
        mavenPom.getMavenProject().getDependencies() == manuallyAddedDependencies + generatedDependencies

        when:
        mavenPom.dependencies = []
        mavenPom.addDependencies(configurations);

        then:
        mavenPom.getMavenProject().getDependencies() == generatedDependencies
    }

    def write() {
        def mavenProjectMock = Mock(MavenProject)
        DefaultMavenPom mavenPom = new DefaultMavenPom(conf2ScopeMappingContainerMock, pomDependenciesConverterStub, mavenProjectMock);
        StringWriter pomWriter = new StringWriter()
        String pomXml = "<project></project>"

        when:
        mavenPom.write(pomWriter)

        then:
        1 * mavenProjectMock.writeModel({actualWriter ->
            actualWriter.write(pomXml)
            true
        })
        pomWriter.toString() == pomXml
    }

    void whenConfigured() {
        def passedMavenPom = null
        mavenPom.whenConfigured { mavenPom ->
            passedMavenPom = mavenPom
        }

        when:
        mavenPom.write(new StringWriter());

        then:
        passedMavenPom.is(mavenPom)
    }

    void withXml() {
        def mavenProjectMock = Mock(MavenProject)
        DefaultMavenPom mavenPom = new DefaultMavenPom(conf2ScopeMappingContainerMock, pomDependenciesConverterStub, mavenProjectMock);
        String pomXml = "<project></project>"
        String passedXml = null
        mavenPom.withXml { xmlProvider ->
            passedXml = xmlProvider.asString()
        }

        when:
        mavenPom.write(new StringWriter());

        then:
        1 * mavenProjectMock.writeModel({actualWriter ->
            actualWriter.write(pomXml)
            true
        })
        passedXml = pomXml
    }

    void notifiesListener() {
        StringWriter pomWriter = new StringWriter()
        mavenPom.addMavenPomListener(new MavenPomListener() {
            public void whenConfigured(MavenPom mavenPom) {
                mavenPom.setInceptionYear("1999");
            }
            public void withXml(XmlProvider xmlProvider) {
                xmlProvider.asString().append("myAppendix")
            }
        })

        when:
        mavenPom.write(pomWriter);

        then:
        mavenPom.inceptionYear == "1999"
        pomWriter.toString().contains("inceptionYear")
        pomWriter.toString().contains("1999")
        pomWriter.toString().endsWith("myAppendix")
    }
}
