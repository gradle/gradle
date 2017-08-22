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
package org.gradle.api.publication.maven.internal.pom

import org.apache.commons.lang.builder.EqualsBuilder
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.gradle.api.Action
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.internal.file.FileResolver
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.artifacts.maven.MavenPom.POM_FILE_ENCODING

class DefaultMavenPomTest extends Specification {
    static final String EXPECTED_PACKAGING = "something";
    static final String EXPECTED_GROUP_ID = "someGroup";
    static final String EXPECTED_ARTIFACT_ID = "artifactId";
    static final String EXPECTED_VERSION = "v\u00E9rsi\u00F8n"; // note the utf-8 chars

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    Conf2ScopeMappingContainer conf2ScopeMappingContainer = Mock()
    PomDependenciesConverter pomDependenciesConverterStub = Mock()
    ConfigurationContainer configurationContainerStub = Mock()
    FileResolver fileResolver = Mock()
    DefaultMavenPom mavenPom = new DefaultMavenPom(configurationContainerStub, conf2ScopeMappingContainer, pomDependenciesConverterStub,
            fileResolver)

    void setup() {
        mavenPom.packaging = EXPECTED_PACKAGING
        mavenPom.groupId = EXPECTED_GROUP_ID
        mavenPom.artifactId = EXPECTED_ARTIFACT_ID
        mavenPom.version = EXPECTED_VERSION
    }

    def init() {
        expect:
        mavenPom.scopeMappings.is(conf2ScopeMappingContainer)
        mavenPom.configurations.is(configurationContainerStub)
        mavenPom.fileResolver.is(fileResolver)
        mavenPom.mavenProject.modelVersion == "4.0.0"
    }

    def setModel() {
        def newModel = new Model()

        when:
        mavenPom.model = newModel

        then:
        mavenPom.model.is(newModel)
    }

    def effectivePomShouldHaveGeneratedDependencies() {
        List generatedDependencies = [new Dependency(groupId: 'someGroup')]
        List manuallyAddedDependencies = [new Dependency()]
        pomDependenciesConverterStub.convert(conf2ScopeMappingContainer, configurationContainerStub) >> generatedDependencies

        when:
        mavenPom.dependencies = manuallyAddedDependencies.clone()

        then:
        EqualsBuilder.reflectionEquals(mavenPom.getEffectivePom().getMavenProject().getDependencies(), manuallyAddedDependencies + generatedDependencies)

        when:
        mavenPom.dependencies = []

        then:
        mavenPom.getEffectivePom().getMavenProject().getDependencies() == generatedDependencies
    }

    def configureActionsShouldBeAppliedAgainstEffectivePom() {
        mavenPom.configurations = null
        when:
        mavenPom.whenConfigured(new Action() {
            void execute(def mavenPom) {
                mavenPom.mavenProject.inceptionYear = '1999'
            }
        })

        then:
        mavenPom.effectivePom.mavenProject.inceptionYear == '1999'
        mavenPom.mavenProject.inceptionYear == null
    }


    def writeShouldUseEffectivePom() {
        List generatedDependencies = [new Dependency(groupId: 'someGroup')]
        pomDependenciesConverterStub.convert(conf2ScopeMappingContainer, configurationContainerStub) >> generatedDependencies

        when:
        StringWriter pomWriter = new StringWriter()
        mavenPom.writeTo pomWriter

        then:
        pomWriter.toString().contains('someGroup')
    }

    def effectivePomWithNullConfigurationsShouldWork() {
        when:
        mavenPom.configurations = null

        then:
        mavenPom.getEffectivePom().getMavenProject().getDependencies() == []
    }

    void projectBuilder() {
        mavenPom.mavenProject.inceptionYear = '2007'
        mavenPom.mavenProject.description = 'some description'
        mavenPom.project {
            inceptionYear '2008'
            licenses {
                license {
                    name 'The Apache Software License, Version 2.0'
                    url 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                    distribution 'repo'
                }
            }
        }

        expect:
        mavenPom.mavenProject.modelVersion == "4.0.0"
        mavenPom.version == EXPECTED_VERSION
        mavenPom.mavenProject.description == 'some description'
        mavenPom.mavenProject.inceptionYear == '2008'
        mavenPom.mavenProject.licenses.size() == 1
        mavenPom.mavenProject.licenses[0].name == 'The Apache Software License, Version 2.0'
        mavenPom.mavenProject.licenses[0].url == 'http://www.apache.org/licenses/LICENSE-2.0.txt'
        mavenPom.mavenProject.licenses[0].distribution == 'repo'
    }

    void actionBasedProjectBuilder() {
        mavenPom.mavenProject.inceptionYear = '2007'
        mavenPom.mavenProject.description = 'some description'
        mavenPom.project({
            it.inceptionYear '2008'
            it.licenses {
                license {
                    name 'The Apache Software License, Version 2.0'
                }
            }
        } as Action<GroovyObject>)

        expect:
        mavenPom.mavenProject.modelVersion == "4.0.0"
        mavenPom.version == EXPECTED_VERSION
        mavenPom.mavenProject.inceptionYear == '2008'
        mavenPom.mavenProject.description == 'some description'
        mavenPom.mavenProject.licenses*.name == ['The Apache Software License, Version 2.0']
    }

    void writeToShouldApplyXmlActions() {
        mavenPom.configurations = null
        StringWriter pomWriter = new StringWriter()

        when:
        mavenPom.withXml {xmlProvider ->
            xmlProvider.asString().append('someAppendix')
        }
        mavenPom.writeTo(pomWriter);

        then:
        pomWriter.toString().endsWith("someAppendix")
    }

    void writeToWritesCorrectPom() {
        mavenPom.configurations = null
        TestFile pomFile = tmpDir.file('someNonexistingDir').file('someFile')
        fileResolver.resolve('file') >> pomFile

        when:
        mavenPom.writeTo('file');

        then:
        pomFile.getText(POM_FILE_ENCODING) == TextUtil.toPlatformLineSeparators("""<?xml version="1.0" encoding="${POM_FILE_ENCODING}"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${EXPECTED_GROUP_ID}</groupId>
  <artifactId>${EXPECTED_ARTIFACT_ID}</artifactId>
  <version>${EXPECTED_VERSION}</version>
  <packaging>${EXPECTED_PACKAGING}</packaging>
</project>
""")
    }
}
