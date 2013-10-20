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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.apache.ivy.core.module.descriptor.License
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomReader.PomDependencyData
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.xml.sax.SAXParseException
import spock.lang.Specification

class PomReaderTest extends Specification {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    PomReader pomReader
    TestFile pomFile
    LocallyAvailableExternalResource locallyAvailableExternalResource

    def setup() {
        pomFile = tmpDir.file('pom.xml')
        pomFile.createFile()
        LocallyAvailableResource locallyAvailableResource = new DefaultLocallyAvailableResource(pomFile)
        locallyAvailableExternalResource = new DefaultLocallyAvailableExternalResource(pomFile.toURI().toURL().toString(), locallyAvailableResource)
    }

    def "parse POM with invalid XML"() {
        when:
        pomFile << """
<projectx>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <description>The first test artifact</description>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        thrown(MetaDataParseException)
    }

    def "parse malformed POM"() {
        when:
        pomFile << """
<someothertag>
    <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>group-one</groupId>
        <artifactId>artifact-one</artifactId>
        <version>version-one</version>
        <name>Test Artifact One</name>
        <description>The first test artifact</description>
    </project>
</someothertag>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        Throwable t = thrown(SAXParseException)
        t.message == 'project must be the root tag'
    }

    def "parse simple POM"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <description>The first test artifact</description>
    <url>http://www.myproject.com</url>
    <licenses>
        <license>
            <name>The Apache Software License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
    </licenses>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.description == 'The first test artifact'
        pomReader.homePage == 'http://www.myproject.com'
        pomReader.packaging == 'jar'
        pomReader.licenses.size() == 1
        pomReader.licenses[0].name == 'The Apache Software License, Version 2.0'
        pomReader.licenses[0].url == 'http://www.apache.org/licenses/LICENSE-2.0.txt'
        !pomReader.hasParent()
        pomReader.pomProperties.size() == 0
        pomReader.properties.size() == 4
        pomReader.properties['parent.version'] == 'version-one'
        pomReader.properties['parent.groupId'] == 'group-one'
        pomReader.properties['project.parent.version'] == 'version-one'
        pomReader.properties['project.parent.groupId'] == 'group-one'
        pomReader.relocation == null
    }

    def "use custom properties in POM project coordinates"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>\${groupId.prop}</groupId>
    <artifactId>\${artifactId.prop}</artifactId>
    <version>\${version.prop}</version>
    <name>Test Artifact One</name>
    <description>The first test artifact</description>
    <properties>
        <some.prop1>test1</some.prop1>
        <some.prop2>test2</some.prop2>
        <groupId.prop>group-one</groupId.prop>
        <artifactId.prop>artifact-one</artifactId.prop>
        <version.prop>version-one</version.prop>
    </properties>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.description == 'The first test artifact'
        pomReader.packaging == 'jar'
        !pomReader.hasParent()
        pomReader.pomProperties.size() == 5
        pomReader.pomProperties.containsKey('some.prop1')
        pomReader.pomProperties.containsKey('some.prop2')
        pomReader.properties.size() == 9
        pomReader.properties.containsKey('some.prop1')
        pomReader.properties.containsKey('some.prop2')
    }

    def "get dependencies without custom properties"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <description>The first test artifact</description>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.getDependencies().size() == 1
        pomReader.getDependencies().containsKey('group-two:artifact-two')
        PomDependencyData dependency = pomReader.getDependencies().get('group-two:artifact-two')
        dependency.groupId == 'group-two'
        dependency.artifactId == 'artifact-two'
        dependency.version == 'version-two'
    }

    def "get dependencies with custom properties"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <description>The first test artifact</description>

    <properties>
        <groupId.prop>group-two</groupId.prop>
        <artifactId.prop>artifact-two</artifactId.prop>
        <version.prop>version-two</version.prop>
    </properties>
    <dependencies>
        <dependency>
            <groupId>\${groupId.prop}</groupId>
            <artifactId>\${artifactId.prop}</artifactId>
            <version>\${version.prop}</version>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.getDependencies().size() == 1
        PomDependencyData dependency = pomReader.getDependencies().get('group-two:artifact-two')
        dependency.groupId == 'group-two'
        dependency.artifactId == 'artifact-two'
        dependency.version == 'version-two'
    }

    def "parse POM with parent POM"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <artifactId>artifact-one</artifactId>

    <parent>
        <groupId>group-two</groupId>
        <artifactId>artifact-two</artifactId>
        <version>version-two</version>
    </parent>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.groupId == 'group-two'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-two'
        pomReader.parentGroupId == 'group-two'
        pomReader.parentArtifactId == 'artifact-two'
        pomReader.parentVersion == 'version-two'
    }

    def "Parse minimal POM and resolve GAV"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)
        pomReader.resolveGAV()

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        pomReader.homePage == ''
        pomReader.description == ''
        pomReader.licenses == new License[0]
        !pomReader.hasParent()
        pomReader.pomProperties.size() == 0
        pomReader.properties.size() == 13
        pomReader.properties['parent.version'] == 'version-one'
        pomReader.properties['parent.groupId'] == 'group-one'
        pomReader.properties['project.parent.version'] == 'version-one'
        pomReader.properties['project.parent.groupId'] == 'group-one'
        pomReader.properties['project.groupId'] == 'group-one'
        pomReader.properties['pom.groupId'] == 'group-one'
        pomReader.properties['groupId'] == 'group-one'
        pomReader.properties['project.artifactId'] == 'artifact-one'
        pomReader.properties['pom.artifactId'] == 'artifact-one'
        pomReader.properties['artifactId'] == 'artifact-one'
        pomReader.properties['project.version'] == 'version-one'
        pomReader.properties['pom.version'] == 'version-one'
        pomReader.properties['version'] == 'version-one'
    }

    def "Parse relocated POM without provided coordinates"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <distributionManagement>
        <relocation>
        </relocation>
    </distributionManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        pomReader.homePage == ''
        pomReader.description == ''
        pomReader.licenses == new License[0]
        !pomReader.hasParent()
        pomReader.pomProperties.size() == 0
        pomReader.relocation != null
        pomReader.relocation.organisation == 'group-one'
        pomReader.relocation.name == 'artifact-one'
        pomReader.relocation.revision == 'version-one'
    }

    def "Parse relocated POM with provided group ID"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <distributionManagement>
        <relocation>
            <groupId>group-two</groupId>
        </relocation>
    </distributionManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        pomReader.homePage == ''
        pomReader.description == ''
        pomReader.licenses == new License[0]
        !pomReader.hasParent()
        pomReader.pomProperties.size() == 0
        pomReader.relocation != null
        pomReader.relocation.organisation == 'group-two'
        pomReader.relocation.name == 'artifact-one'
        pomReader.relocation.revision == 'version-one'
    }

    def "Parse relocated POM with provided group ID and artifact ID"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <distributionManagement>
        <relocation>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </relocation>
    </distributionManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        pomReader.homePage == ''
        pomReader.description == ''
        pomReader.licenses == new License[0]
        !pomReader.hasParent()
        pomReader.pomProperties.size() == 0
        pomReader.relocation != null
        pomReader.relocation.organisation == 'group-two'
        pomReader.relocation.name == 'artifact-two'
        pomReader.relocation.revision == 'version-one'
    }

    def "Parse relocated POM with all provided coordinates"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <distributionManagement>
        <relocation>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
        </relocation>
    </distributionManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        pomReader.homePage == ''
        pomReader.description == ''
        pomReader.licenses == new License[0]
        !pomReader.hasParent()
        pomReader.pomProperties.size() == 0
        pomReader.relocation != null
        pomReader.relocation.organisation == 'group-two'
        pomReader.relocation.name == 'artifact-two'
        pomReader.relocation.revision == 'version-two'
    }
}
