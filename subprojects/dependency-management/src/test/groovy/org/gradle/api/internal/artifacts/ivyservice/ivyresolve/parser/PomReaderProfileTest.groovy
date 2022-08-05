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

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomProfile
import org.gradle.internal.id.UUIDGenerator
import spock.lang.Issue

class PomReaderProfileTest extends AbstractPomReaderTest {
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock()

    def "parse POM without active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation/>
        </profile>
        <profile>
            <id>profile-3</id>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.parseActivePomProfiles().size() == 0
    }

    def "parse POM with multiple active profiles having the same ID"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
        </profile>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.parseActivePomProfiles().size() == 4
    }

    def "parse POM with a mix of active and inactive profiles"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-two</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <prop2>myproperty2</prop2>
            </properties>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-three</groupId>
                        <artifactId>artifact-three</artifactId>
                        <version>version-three</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
        <profile>
            <id>profile-3</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
            <properties>
                <prop3>myproperty3</prop3>
            </properties>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-four</groupId>
                        <artifactId>artifact-four</artifactId>
                        <version>version-four</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.parseActivePomProfiles().size() == 1
        pomReader.properties.size() == 7
        !pomReader.properties.containsKey('prop1')
        !pomReader.properties.containsKey('prop3')
        pomReader.properties['prop2'] == 'myproperty2'
        !pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-two', 'jar', null))
        !pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-four', 'artifact-four', 'jar', null))
        pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-three', 'artifact-three', 'jar', null))
    }

    def "cannot use POM property to set profile ID"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <properties>
        <activate.id>profile-1</activate.id>
    </properties>
    <profiles>
        <profile>
            <id>\${activate.id}</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 1
        activePomProfiles[0].id == '${activate.id}'
    }

    def "cannot use POM property to control profile activation"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <properties>
        <activate.profile>true</activate.profile>
    </properties>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>\${activate.profile}</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 0
        !pomReader.properties.containsKey('groupId.prop')
        !pomReader.properties.containsKey('artifactId.prop')
        !pomReader.properties.containsKey('version.prop')
    }

    def "parse POM with active profile providing properties"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 1
        PomProfile pomProfile = activePomProfiles[0]
        pomProfile
        pomProfile.id == 'profile-1'
        pomProfile.properties.size() == 3
        pomProfile.properties['groupId.prop'] == 'group-two'
        pomProfile.properties['artifactId.prop'] == 'artifact-two'
        pomProfile.properties['version.prop'] == 'version-two'
        pomReader.properties.size() == 9
        pomReader.properties['groupId.prop'] == 'group-two'
        pomReader.properties['artifactId.prop'] == 'artifact-two'
        pomReader.properties['version.prop'] == 'version-two'
    }

    def "parse POM with multiple active profile providing same properties"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.parseActivePomProfiles().size() == 2
        pomReader.properties.size() == 9
        pomReader.properties['groupId.prop'] == 'group-two'
        pomReader.properties['artifactId.prop'] == 'artifact-two'
        pomReader.properties['version.prop'] == 'version-two'
    }

    def "get dependencies with custom properties of active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <dependencies>
        <dependency>
            <groupId>\${groupId.prop}</groupId>
            <artifactId>\${artifactId.prop}</artifactId>
            <version>\${version.prop}</version>
        </dependency>
    </dependencies>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-two')
    }

    def "custom properties from last active profile determines dependency coordinates"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <dependencies>
        <dependency>
            <groupId>\${groupId.prop}</groupId>
            <artifactId>\${artifactId.prop}</artifactId>
            <version>\${version.prop}</version>
        </dependency>
    </dependencies>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-three</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-three')
    }

    def "properties from an active profile override existing POM properties"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
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
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-three</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-three')
    }

    def "multiple active profiles can determine different dependency coordinates"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <dependencies>
        <dependency>
            <groupId>\${groupId.prop}</groupId>
            <artifactId>\${artifactId.prop}</artifactId>
            <version>\${version.prop}</version>
        </dependency>
        <dependency>
            <groupId>\${otherGroupId.prop}</groupId>
            <artifactId>\${otherArtifactId.prop}</artifactId>
            <version>\${otherVersion.prop}</version>
        </dependency>
    </dependencies>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <otherGroupId.prop>group-three</otherGroupId.prop>
                <otherArtifactId.prop>artifact-three</otherArtifactId.prop>
                <otherVersion.prop>version-three</otherVersion.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyDep1 = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyDep2 = new MavenDependencyKey('group-three', 'artifact-three', 'jar', null)

        then:
        pomReader.getDependencies().size() == 2
        assertResolvedPomDependency(keyDep1, 'version-two')
        assertResolvedPomDependency(keyDep2, 'version-three')
    }

    def "get dependencies management without properties from active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-two</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 1
        activePomProfiles[0].getDependencyMgts().size() == 1
        assertResolvedPomDependencyManagement(key, 'version-two')
    }

    def "get dependencies management with properties from active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
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
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 1
        activePomProfiles[0].getDependencyMgts().size() == 1
        assertResolvedPomDependencyManagement(key, 'version-two')
    }

    def "finds dependency default with properties defined in main body of POM"() {
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
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>\${groupId.prop}</groupId>
                        <artifactId>\${artifactId.prop}</artifactId>
                        <version>\${version.prop}</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(key, 'version-two')
        pomReader.findDependencyDefaults(key) == pomReader.dependencyMgt[key]
    }

    def "finds dependency default if declared in active profile"() {
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
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-three</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-two</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
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

    def "uses dependency default from active profile over POM dependency default"() {
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
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>version-two</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-three</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(keyGroupTwo, 'version-three')
        pomReader.findDependencyDefaults(keyGroupTwo) == pomReader.dependencyMgt[keyGroupTwo]
    }

    def "finds dependency default if declared in multiple active profiles"() {
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
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-three</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-two</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-three</artifactId>
                        <version>version-three</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyGroupTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyGroupThree = new MavenDependencyKey('group-two', 'artifact-three', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 2
        assertResolvedPomDependencyManagement(keyGroupTwo, 'version-two')
        pomReader.findDependencyDefaults(keyGroupTwo) == pomReader.dependencyMgt[keyGroupTwo]
        assertResolvedPomDependencyManagement(keyGroupThree, 'version-three')
        pomReader.findDependencyDefaults(keyGroupThree) == pomReader.dependencyMgt[keyGroupThree]
    }

    def "finds dependency default if declared in parent POM active profile"() {
        when:
        String parentPom = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-two</groupId>
    <artifactId>artifact-two</artifactId>
    <version>version-two</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-two</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
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

    def "uses child properties over parent properties if defined in active profile with the different values"() {
        when:
        String parentPom = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-two</groupId>
    <artifactId>artifact-two</artifactId>
    <version>version-two</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-three</groupId.prop>
                <artifactId.prop>artifact-three</artifactId.prop>
                <version.prop>version-three</version.prop>
            </properties>
        </profile>
    </profiles>
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
            <groupId>\${groupId.prop}</groupId>
            <artifactId>\${artifactId.prop}</artifactId>
            <version>\${version.prop}</version>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <groupId.prop>group-four</groupId.prop>
                <artifactId.prop>artifact-four</artifactId.prop>
                <version.prop>version-four</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        pomReader.setPomParent(parentPomReader)
        MavenDependencyKey key = new MavenDependencyKey('group-four', 'artifact-four', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-four')
    }

    def "gets dependencies declared in a single active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
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
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyArtifactTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyArtifactThree = new MavenDependencyKey('group-three', 'artifact-three', 'jar', null)

        then:
        pomReader.getDependencies().size() == 2
        assertResolvedPomDependency(keyArtifactTwo, 'version-two')
        assertResolvedPomDependency(keyArtifactThree, 'version-three')
    }

    def "uses last declaration of dependency with same groupId and artifactId in a single active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-three')
    }

    def "gets dependencies declared in a multiple active profiles"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
        <profile>
            <id>profile-3</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-four</groupId>
                    <artifactId>artifact-four</artifactId>
                    <version>version-four</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey keyArtifactTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyArtifactThree = new MavenDependencyKey('group-three', 'artifact-three', 'jar', null)
        MavenDependencyKey keyArtifactFour = new MavenDependencyKey('group-four', 'artifact-four', 'jar', null)

        then:
        pomReader.getDependencies().size() == 3
        assertResolvedPomDependency(keyArtifactTwo, 'version-two')
        assertResolvedPomDependency(keyArtifactThree, 'version-three')
        assertResolvedPomDependency(keyArtifactFour, 'version-four')
    }

    def "uses last declaration of dependency with same groupId and artifactId in multiple active profiles"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-three')
    }

    def "gets dependency with provided properties declared in active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
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
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-two')
    }

    def "gets dependency in active profile with provided properties in POM main body"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <properties>
        <groupId.prop>group-two</groupId.prop>
        <artifactId.prop>artifact-two</artifactId.prop>
        <version.prop>version-two</version.prop>
    </properties>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>\${groupId.prop}</groupId>
                    <artifactId>\${artifactId.prop}</artifactId>
                    <version>\${version.prop}</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-two')
    }

    def "gets dependency with provided defaults declared in active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
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
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(key, 'version-two')
        pomReader.findDependencyDefaults(key) == pomReader.dependencyMgt[key]
    }

    def "gets dependency with provided defaults declared in POM main body"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>version-two</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencyMgt().size() == 1
        assertResolvedPomDependencyManagement(key, 'version-two')
        pomReader.findDependencyDefaults(key) == pomReader.dependencyMgt[key]
    }

    def "uses active profile dependency over dependency with same groupId and artifactId declared in POM main body"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>

    <dependency>
        <groupId>group-two</groupId>
        <artifactId>artifact-two</artifactId>
        <version>version-two</version>
    </dependency>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-three')
    }

    def "uses dependency if declared in parent POM active profile"() {
        when:
        String parentPom = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-two</groupId>
    <artifactId>artifact-two</artifactId>
    <version>version-two</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        pomReader.setPomParent(parentPomReader)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-two')
    }

    def "uses dependency if declared in parent and child POM active profile"() {
        when:
        String parentPom = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-two</groupId>
    <artifactId>artifact-two</artifactId>
    <version>version-two</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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

    <profiles>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        pomReader.setPomParent(parentPomReader)
        MavenDependencyKey keyArtifactTwo = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)
        MavenDependencyKey keyArtifactThree = new MavenDependencyKey('group-three', 'artifact-three', 'jar', null)

        then:
        pomReader.getDependencies().size() == 2
        assertResolvedPomDependency(keyArtifactTwo, 'version-two')
        assertResolvedPomDependency(keyArtifactThree, 'version-three')
    }

    def "uses child dependency over parent dependency in POM active profile"() {
        when:
        String parentPom = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-two</groupId>
    <artifactId>artifact-two</artifactId>
    <version>version-two</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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

    <profiles>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
        pomReader.setPomParent(parentPomReader)
        MavenDependencyKey key = new MavenDependencyKey('group-two', 'artifact-two', 'jar', null)

        then:
        pomReader.getDependencies().size() == 1
        assertResolvedPomDependency(key, 'version-three')
    }

    def "cannot use POM property to control profile property activation"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <properties>
        <activate.profile>BLUE</activate.profile>
    </properties>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>\${activate.profile}</value>
                </property>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 0
        !pomReader.properties.containsKey('groupId.prop')
        !pomReader.properties.containsKey('artifactId.prop')
        !pomReader.properties.containsKey('version.prop')

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "does not activate profile for a matching system property value"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
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
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 0
        !pomReader.properties.containsKey('prop1')
        !pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-two', 'jar', null))

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "does not activate multiple profiles for a matching same system property with the same value"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <prop2>myproperty2</prop2>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 0
        !pomReader.properties.containsKey('prop1')
        !pomReader.properties.containsKey('prop2')

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "does not activate multiple profiles for a matching different system property with different values"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()
        String someOtherPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'
        System.properties[someOtherPropertyName] = 'GREEN'

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <property>
                    <name>${someOtherPropertyName}</name>
                    <value>GREEN</value>
                </property>
            </activation>
            <properties>
                <prop2>myproperty2</prop2>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 0
        !pomReader.properties.containsKey('prop1')
        !pomReader.properties.containsKey('prop2')

        cleanup:
        System.clearProperty(customPropertyName)
        System.clearProperty(someOtherPropertyName)
    }

    def "does not activate profile if system property value is not matching"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'GREEN'

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
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
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 0
        !pomReader.properties.containsKey('prop1')
        !pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-two', 'jar', null))
        !pomReader.dependencies.containsKey(new MavenDependencyKey('group-three', 'artifact-three', 'jar', null))

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "activates profile by the absence of a property"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!${customPropertyName}</name>
                </property>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
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
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 1
        activePomProfiles[0].id == 'profile-1'
        pomReader.properties['prop1'] == 'myproperty1'
        pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-two', 'jar', null))
        assertResolvedPomDependency(new MavenDependencyKey('group-three', 'artifact-three', 'jar', null), 'version-three')
    }

    def "activates profile for negated property if system property is provided"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!${customPropertyName}</name>
                </property>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
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
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 1
        activePomProfiles[0].id == 'profile-1'
        pomReader.properties['prop1'] == 'myproperty1'
        pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-two', 'jar', null))
        assertResolvedPomDependency(new MavenDependencyKey('group-three', 'artifact-three', 'jar', null), 'version-three')

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "does not activate profile if property value is not declared and system property is not set"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                </property>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
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
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 0
        !pomReader.properties.containsKey('prop1')
        !pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-two', 'jar', null))
        !pomReader.dependencies.containsKey(new MavenDependencyKey('group-three', 'artifact-three', 'jar', null))
    }

    def "does not activate profile if property value is not declared and system property is set with any value"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                </property>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
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
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 0
        !pomReader.properties.containsKey('prop1')
        !pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-two', 'jar', null))
        !pomReader.dependencies.containsKey(new MavenDependencyKey('group-three', 'artifact-three', 'jar', null))

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "system property activation removes all other profiles that are active by default"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <prop1>myproperty1</prop1>
            </properties>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-one</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
            <dependencies>
                <dependency>
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-one</version>
                </dependency>
            </dependencies>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <property>
                    <name>!${customPropertyName}</name>
                </property>
            </activation>
            <properties>
                <prop2>myproperty2</prop2>
            </properties>
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
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
        <profile>
            <id>profile-3</id>
            <activation>
                <property>
                    <name>!${customPropertyName}</name>
                </property>
            </activation>
            <properties>
                <prop3>myproperty3</prop3>
            </properties>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-three</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
            <dependencies>
                <dependency>
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-three</version>
                </dependency>
            </dependencies>
        </profile>
        <profile>
            <id>profile-4</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <prop4>myproperty4</prop4>
            </properties>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-four</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
            <dependencies>
                <dependency>
                    <groupId>group-three</groupId>
                    <artifactId>artifact-three</artifactId>
                    <version>version-four</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 2
        activePomProfiles[0].id == 'profile-2'
        activePomProfiles[1].id == 'profile-3'
        !pomReader.properties.containsKey('prop1')
        !pomReader.properties.containsKey('prop4')
        pomReader.properties['prop2'] == 'myproperty2'
        pomReader.properties['prop3'] == 'myproperty3'
        !pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-one', 'jar', null))
        !pomReader.dependencyMgt.containsKey(new MavenDependencyKey('group-two', 'artifact-four', 'jar', null))
        assertResolvedPomDependencyManagement(new MavenDependencyKey('group-two', 'artifact-two', 'jar', null), 'version-three')
        assertResolvedPomDependency(new MavenDependencyKey('group-three', 'artifact-three', 'jar', null), 'version-three')
    }

    def "parse POM with multiple active profiles activated by absence of system property"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()

        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                 <property>
                    <name>!${customPropertyName}</name>
                </property>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                 <property>
                    <name>!${customPropertyName}</name>
                </property>
            </activation>
            <properties>
                <groupId.prop>group-two</groupId.prop>
                <artifactId.prop>artifact-two</artifactId.prop>
                <version.prop>version-two</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.parseActivePomProfiles().size() == 2
        pomReader.properties.size() == 9
        pomReader.properties['groupId.prop'] == 'group-two'
        pomReader.properties['artifactId.prop'] == 'artifact-two'
        pomReader.properties['version.prop'] == 'version-two'
    }

    def "finds dependency default if declared in parent POM profile activated by absence of system property"() {
        setup:
        String customPropertyName = new UUIDGenerator().generateId()

        when:
        String parentPom = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-two</groupId>
    <artifactId>artifact-two</artifactId>
    <version>version-two</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!${customPropertyName}</name>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>version-two</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
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
    def "can define #packaging packaging in active profile"() {
        when:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <packaging>\${package.type}</packaging>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <package.type>$packaging</package.type>
            </properties>
        </profile>
    </profiles>
</project>
"""
        pomReader = new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)

        then:
        pomReader.groupId == 'group-one'
        pomReader.artifactId == 'artifact-one'
        pomReader.version == 'version-one'
        pomReader.packaging == packaging
        List<PomProfile> activePomProfiles = pomReader.parseActivePomProfiles()
        activePomProfiles.size() == 1
        activePomProfiles[0].properties.size() == 1
        activePomProfiles[0].properties.containsKey('package.type')
        activePomProfiles[0].properties['package.type'] == packaging

        where:
        packaging << ['pom', 'jar', 'ejb', 'war', 'ear', 'rar', 'par']
    }
}
