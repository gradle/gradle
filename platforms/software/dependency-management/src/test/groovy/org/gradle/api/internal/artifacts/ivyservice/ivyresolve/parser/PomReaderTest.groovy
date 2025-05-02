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

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey
import org.xml.sax.SAXParseException
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class PomReaderTest extends AbstractPomReaderTest {
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock()

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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        Throwable t = thrown(SAXParseException)
        t.message == 'project must be the root tag'
    }

    def "parse POM with external entities"() {
        given:
        def externalFile = tmpDir.file('external.txt').createFile()
        pomFile << """
        <!DOCTYPE data [
          <!ENTITY file SYSTEM "file://${normaliseFileSeparators(externalFile.absolutePath)}">
        ]>
        <project>
            <modelVersion>4.0.0</modelVersion>
            <groupId>group-one</groupId>
            <artifactId>artifact-one</artifactId>
            <version>&file;</version>
        </project>
        """

        when:
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        def e = thrown(MetaDataParseException)
        e.message.startsWith("Could not parse POM")
        e.cause.message.contains("Already seen doctype")
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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        !pomReader.hasParent()
        pomReader.properties.size() == 6
        pomReader.properties['parent.version'] == 'version-one'
        pomReader.properties['parent.groupId'] == 'group-one'
        pomReader.properties['parent.artifactId'] == 'artifact-one'
        pomReader.properties['project.parent.version'] == 'version-one'
        pomReader.properties['project.parent.groupId'] == 'group-one'
        pomReader.properties['project.parent.artifactId'] == 'artifact-one'
        pomReader.relocation == null
        !pomReader.hasGradleMetadataMarker()
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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        !pomReader.hasParent()
        pomReader.properties.size() == 11
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

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-two')
    }

    def "get dependencies with custom properties"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-two')
    }

    @Issue("GRADLE-2931")
    def "picks version of last dependency defined by artifact ID, group ID, type and classifier"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>version-two</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>version-three</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>version-four</version>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', 'myjar')

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-four')
    }

    @Issue("GRADLE-2931")
    def "can declare multiple dependencies with same artifact ID and group ID but different type and classifier"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>version-two</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>test-jar</type>
            <classifier>test</classifier>
            <version>version-three</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>ejb-client</type>
            <classifier>client</classifier>
            <version>version-four</version>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey dependencyJarkey = new MavenDependencyKey('group-two', 'artifact-two', 'jar', 'myjar')
        MavenDependencyKey dependencyTestJarkey = new MavenDependencyKey('group-two', 'artifact-two', 'test-jar', 'test')
        MavenDependencyKey dependencyEjbClientKey = new MavenDependencyKey('group-two', 'artifact-two', 'ejb-client', 'client')

        then:
        pomReader.getDependencies().size() == 3
        assertResolvedPomDependency(dependencyJarkey, 'version-two')
        assertResolvedPomDependency(dependencyTestJarkey, 'version-three')
        assertResolvedPomDependency(dependencyEjbClientKey, 'version-four')
    }

    def "get dependencies management without custom properties"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>version-two</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(key, 'version-two')
    }

    def "get dependencies management with custom properties"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <properties>
        <groupId.prop>group-two</groupId.prop>
        <artifactId.prop>artifact-two</artifactId.prop>
        <version.prop>version-two</version.prop>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>\${groupId.prop}</groupId>
                <artifactId>\${artifactId.prop}</artifactId>
                <version>\${version.prop}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(key, 'version-two')
    }

    def "picks version of last dependency management defined by artifact ID, group ID, type and classifier"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <type>jar</type>
                <classifier>myjar</classifier>
                <version>version-two</version>
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <type>jar</type>
                <classifier>myjar</classifier>
                <version>version-three</version>
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <type>jar</type>
                <classifier>myjar</classifier>
                <version>version-four</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', 'myjar')

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(key, 'version-four')
    }

    def "can declare multiple dependency managements with same artifact ID and group ID but different type and classifier"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <type>jar</type>
                <classifier>myjar</classifier>
                <version>version-two</version>
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <type>test-jar</type>
                <classifier>test</classifier>
                <version>version-three</version>
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <type>ejb-client</type>
                <classifier>client</classifier>
                <version>version-four</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey dependencyJarkey = new MavenDependencyKey('group-two', 'artifact-two', 'jar', 'myjar')
        MavenDependencyKey dependencyTestJarkey = new MavenDependencyKey('group-two', 'artifact-two', 'test-jar', 'test')
        MavenDependencyKey dependencyEjbClientKey = new MavenDependencyKey('group-two', 'artifact-two', 'ejb-client', 'client')

        then:
        pomReader.getDependencyMgt().size() == 3
        assertResolvedPomDependencyManagement(dependencyJarkey, 'version-two')
        assertResolvedPomDependencyManagement(dependencyTestJarkey, 'version-three')
        assertResolvedPomDependencyManagement(dependencyEjbClientKey, 'version-four')
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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        pomReader.resolveGAV()

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        !pomReader.hasParent()
        pomReader.properties.size() == 15
        pomReader.properties['parent.version'] == 'version-one'
        pomReader.properties['parent.groupId'] == 'group-one'
        pomReader.properties['parent.artifactId'] == 'artifact-one'
        pomReader.properties['project.parent.version'] == 'version-one'
        pomReader.properties['project.parent.groupId'] == 'group-one'
        pomReader.properties['project.parent.artifactId'] == 'artifact-one'
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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        !pomReader.hasParent()
        pomReader.relocation != null
        pomReader.relocation == DefaultModuleVersionIdentifier.newId('group-one', 'artifact-one', 'version-one')
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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        !pomReader.hasParent()
        pomReader.relocation != null
        pomReader.relocation == DefaultModuleVersionIdentifier.newId('group-two', 'artifact-one', 'version-one')
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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        !pomReader.hasParent()
        pomReader.relocation != null
        pomReader.relocation == DefaultModuleVersionIdentifier.newId('group-two', 'artifact-two', 'version-one')
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
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == 'jar'
        !pomReader.hasParent()
        pomReader.relocation != null
        pomReader.relocation == DefaultModuleVersionIdentifier.newId('group-two', 'artifact-two', 'version-two')
    }

    @Issue("GRADLE-2938")
    def "uses default type for dependency if not declared"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
        </dependency>
        <dependency>
            <groupId>group-three</groupId>
            <artifactId>artifact-three</artifactId>
            <version>version-three</version>
            <type>jar</type>
        </dependency>
        <dependency>
            <groupId>group-four</groupId>
            <artifactId>artifact-four</artifactId>
            <version>version-four</version>
            <type>ejb-client</type>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyGroupThree = new MavenDependencyKey('group-three', 'artifact-three', 'jar', null)
        MavenDependencyKey keyGroupFour = new MavenDependencyKey('group-four', 'artifact-four', 'ejb-client', null)

        then:
        pomReader.getDependencies().size() == 3
        assertResolvedPomDependency(keyGroupTwo, 'version-two')
        assertResolvedPomDependency(keyGroupThree, 'version-three')
        assertResolvedPomDependency(keyGroupFour, 'version-four')
    }

    @Issue("GRADLE-2938")
    def "uses default type for dependency management if not declared"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>version-two</version>
            </dependency>
            <dependency>
                <groupId>group-three</groupId>
                <artifactId>artifact-three</artifactId>
                <version>version-three</version>
                <type>jar</type>
            </dependency>
            <dependency>
                <groupId>group-four</groupId>
                <artifactId>artifact-four</artifactId>
                <version>version-four</version>
                <type>ejb-client</type>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyGroupThree = new MavenDependencyKey('group-three', 'artifact-three', 'jar', null)
        MavenDependencyKey keyGroupFour = new MavenDependencyKey('group-four', 'artifact-four', 'ejb-client', null)

        then:
        pomReader.getDependencyMgt().size() == 3
        assertResolvedPomDependencyManagement(keyGroupTwo, 'version-two')
        assertResolvedPomDependencyManagement(keyGroupThree, 'version-three')
        assertResolvedPomDependencyManagement(keyGroupFour, 'version-four')
    }

    def "finds dependency default if declared in same POM"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>version-two</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-three</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyGroupThree = new MavenDependencyKey('group-two', 'artifact-three', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(keyGroupTwo, 'version-two')
        pomReader.findDependencyDefaults(keyGroupTwo) == pomReader.dependencyMgt[keyGroupTwo]
        !pomReader.findDependencyDefaults(keyGroupThree)
    }

    def "finds dependency default if declared in parent POM"() {
        when:
        String parentPom = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-two</groupId>
    <artifactId>artifact-two</artifactId>
    <version>version-two</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>version-two</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        PomReader parentPomReader = createPomReader('parent-pom.xml', parentPom)
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-two</groupId>
        <artifactId>artifact-two</artifactId>
        <version>version-two</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>group-four</groupId>
            <artifactId>artifact-four</artifactId>
        </dependency>
        <dependency>
            <groupId>group-five</groupId>
            <artifactId>artifact-five</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        pomReader.setPomParent(parentPomReader)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyGroupThree = new MavenDependencyKey('group-two', 'artifact-three', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(keyGroupTwo, 'version-two')
        pomReader.findDependencyDefaults(keyGroupTwo) == pomReader.dependencyMgt[keyGroupTwo]
        !pomReader.findDependencyDefaults(keyGroupThree)
    }

    @Issue("GRADLE-3074")
    def "can define #packaging packaging with custom property"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <packaging>\${package.type}</packaging>

    <properties>
        <package.type>$packaging</package.type>
    </properties>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == packaging
        pomReader.properties.containsKey('package.type')
        pomReader.properties['package.type'] == packaging

        where:
        packaging << ['pom', 'jar', 'ejb', 'war', 'ear', 'rar', 'par']
    }

    @Issue("GRADLE-3299")
    def "can define GAV with reference to parent.GAV"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>\${parent.groupId}</groupId>
    <artifactId>\${parent.artifactId}</artifactId>
    <version>\${parent.version}</version>

    <parent>
        <groupId>parent-group</groupId>
        <artifactId>parent-artifact</artifactId>
        <version>parent-version</version>
    </parent>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.parentGroupId == 'parent-group'
        pomReader.parentArtifactId == 'parent-artifact'
        pomReader.parentVersion == 'parent-version'
        pomReader.groupId == pomReader.parentGroupId
        pomReader.artifactId == pomReader.parentArtifactId
        pomReader.version == pomReader.parentVersion
    }

    @Issue("gradle/gradle#5092")
    def 'can parse exclusion defined only by artifactId'() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <exclusions>
                <exclusion>
                    <artifactId>bar</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        moduleIdentifierFactory.module('*', 'bar') >> DefaultModuleIdentifier.newId('*', 'bar')

        then:
        def excluded = pomReader.dependencies[keyGroupTwo].excludedModules
        excluded == [DefaultModuleIdentifier.newId("*", "bar")]
    }

    @Issue("gradle/gradle#5092")
    def 'can parse exclusion defined only by groupId'() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <exclusions>
                <exclusion>
                    <groupId>bar</groupId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        moduleIdentifierFactory.module('bar', '*') >> DefaultModuleIdentifier.newId('bar', '*')

        then:
        def excluded = pomReader.dependencies[keyGroupTwo].excludedModules
        excluded == [DefaultModuleIdentifier.newId('bar', '*')]
    }

    @Issue("gradle/gradle#5092")
    def 'can parse exclusion defined by groupId and artifactId'() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <exclusions>
                <exclusion>
                    <groupId>bar</groupId>
                    <artifactId>bar</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        moduleIdentifierFactory.module('bar', 'bar') >> DefaultModuleIdentifier.newId('bar', 'bar')

        then:
        def excluded = pomReader.dependencies[keyGroupTwo].excludedModules
        excluded == [DefaultModuleIdentifier.newId('bar', 'bar')]
    }

    @Issue("gradle/gradle#5092")
    def 'ignores empty exclusion block'() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <exclusions>
                <exclusion>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.dependencies[keyGroupTwo].excludedModules.isEmpty()
        0 * moduleIdentifierFactory.module(_, _)
    }

    @Issue("gradle/gradle#5092")
    def 'can parse a wildcard exclusion block'() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <exclusions>
                <exclusion>
                    <groupId>*</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        moduleIdentifierFactory.module('*', '*') >> DefaultModuleIdentifier.newId('*', '*')

        then:
        def excluded = pomReader.dependencies[keyGroupTwo].excludedModules
        excluded == [DefaultModuleIdentifier.newId('*', '*')]
    }

    @Issue("gradle/gradle#22370")
    def 'can parse an exclusion block with properties'() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>

    <properties>
        <excludedGroupId>excluded-group-id</excludedGroupId>
        <excludedArtifactId>excluded-artifact-id</excludedArtifactId>
    </properties>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <exclusions>
                <exclusion>
                    <groupId>\${excludedGroupId}</groupId>
                    <artifactId>\${excludedArtifactId}</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        def keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        moduleIdentifierFactory.module('excluded-group-id', 'excluded-artifact-id')
            >> DefaultModuleIdentifier.newId('excluded-group-id', 'excluded-artifact-id')

        then:
        def excluded = pomReader.dependencies[keyGroupTwo].excludedModules
        excluded == [DefaultModuleIdentifier.newId('excluded-group-id', 'excluded-artifact-id')]
    }

    def 'parses old gradle module metadata marker'() {
        when:
        pomFile << """
<project>
    <!-- do-not-remove: published-with-gradle-metadata -->
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.hasGradleMetadataMarker()
    }

    def 'parses gradle module metadata marker'() {
        when:
        pomFile << """
<project>
    <!-- do_not_remove: published-with-gradle-metadata -->
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.hasGradleMetadataMarker()
    }

    @Issue("gradle/gradle#26110")
    def 'can parse optional with whitespace'() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
    <version>version</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <optional>  true\t</optional>
        </dependency>
    </dependencies>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        moduleIdentifierFactory.module('*', '*') >> DefaultModuleIdentifier.newId('*', '*')

        then:
        pomReader.dependencies[keyGroupTwo].optional
    }
}
