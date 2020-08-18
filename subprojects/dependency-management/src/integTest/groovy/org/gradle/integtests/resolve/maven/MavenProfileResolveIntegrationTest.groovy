/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.internal.id.UUIDGenerator
import spock.lang.Issue

class MavenProfileResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        settingsFile << "rootProject.name = 'test' "
        resolve = new ResolveTestFixture(buildFile, "compile")
        resolve.prepare()
        resolve.expectDefaultConfiguration('runtime')
        resolve.addDefaultVariantDerivationStrategy()
    }

    def "uses properties from active profile to resolve dependency"() {
        given:
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
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
                <groupId.prop>groupB</groupId.prop>
                <artifactId.prop>artifactB</artifactId.prop>
                <version.prop>1.4</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.4").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:unspecified") {
                module("groupA:artifactA:1.2") {
                    module("groupB:artifactB:1.4")
                }
            }
        }
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2861")
    @ToBeFixedForConfigurationCache
    def "uses properties from active profile to resolve dependency with placeholders in dependency management"() {
        given:
        def parent = mavenHttpRepo.module('group', 'parent', '1.0').publish()
        parent.pomFile.text = parent.pomFile.text.replace("</project>", '''
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>${some.group}</groupId>
                <artifactId>${some.artifact}</artifactId>
                <version>${some.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>groupB</some.group>
                <some.artifact>artifactB</some.artifact>
                <some.version>1.4</some.version>
            </properties>
        </profile>
    </profiles>
</project>
''')

        def moduleA = mavenHttpRepo.module('groupA', 'artifactA', '1.2')
                .parent('group', 'parent', '1.0')
                .dependsOn('groupB', 'artifactB', null)
                .publish()

        def moduleB = mavenHttpRepo.module('groupB', 'artifactB', '1.4').publish()

        and:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations { compile }
            dependencies { compile 'groupA:artifactA:1.2' }
            task libs { doLast { assert configurations.compile.files*.name == ['artifactA-1.2.jar', 'artifactB-1.4.jar'] } }
        """

        when:
        parent.pom.expectGet()
        moduleA.pom.expectGet()
        moduleA.artifact.expectGet()
        moduleB.pom.expectGet()
        moduleB.artifact.expectGet()

        then:
        // have to run twice to trigger the failure, to parse the descriptor from the cache
        succeeds ":libs"
        succeeds ":libs"

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:unspecified") {
                module("groupA:artifactA:1.2") {
                    module("groupB:artifactB:1.4")
                }
            }
        }
    }

    def "uses dependency management defaults from active profile to resolve dependency"() {
        given:
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
    <dependencies>
        <dependency>
            <groupId>groupB</groupId>
            <artifactId>artifactB</artifactId>
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
                        <groupId>groupB</groupId>
                        <artifactId>artifactB</artifactId>
                        <version>1.4</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.4").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:unspecified") {
                module("groupA:artifactA:1.2") {
                    module("groupB:artifactB:1.4")
                }
            }
        }
    }

    def "resolves dependency from active profile"() {
        given:
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
    <dependencies>
        <dependency>
            <groupId>groupB</groupId>
            <artifactId>artifactB</artifactId>
            <version>1.3</version>
        </dependency>
        <dependency>
            <groupId>groupB</groupId>
            <artifactId>artifactB</artifactId>
            <version>1.7</version>
        </dependency>
    </dependencies>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>groupB</groupId>
                    <artifactId>artifactB</artifactId>
                    <version>1.4</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.4").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:unspecified") {
                module("groupA:artifactA:1.2") {
                    module("groupB:artifactB:1.4")
                }
            }
        }
    }

    def "uses properties from profile activated by the absence of a property"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
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
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <properties>
                <groupId.prop>groupB</groupId.prop>
                <artifactId.prop>artifactB</artifactId.prop>
                <version.prop>1.4</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.4").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:unspecified") {
                module("groupA:artifactA:1.2") {
                    module("groupB:artifactB:1.4")
                }
            }
        }
    }

    def "uses properties from profile activated by absence of system property over active by default to resolve dependency"() {
        given:
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
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
                <groupId.prop>groupB</groupId.prop>
                <artifactId.prop>artifactB</artifactId.prop>
                <version.prop>1.4</version.prop>
            </properties>
        </profile>
        <profile>
            <id>profile-2</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <properties>
                <groupId.prop>groupB</groupId.prop>
                <artifactId.prop>artifactB</artifactId.prop>
                <version.prop>1.5</version.prop>
            </properties>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.5").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:unspecified") {
                module("groupA:artifactA:1.2") {
                    module("groupB:artifactB:1.5")
                }
            }
        }
    }

    def "uses dependency management defaults from profile activated by absence of system property to resolve dependency"() {
        given:
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
    <dependencies>
        <dependency>
            <groupId>groupB</groupId>
            <artifactId>artifactB</artifactId>
        </dependency>
    </dependencies>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>groupB</groupId>
                        <artifactId>artifactB</artifactId>
                        <version>1.4</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.4").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:unspecified") {
                module("groupA:artifactA:1.2") {
                    module("groupB:artifactB:1.4")
                }
            }
        }
    }

    def "resolves dependency from profile activated by absence of system property"() {
        given:
        def requestedModule = mavenHttpRepo.module("groupA", "artifactA", "1.2").publish()
        requestedModule.pomFile.text = """
<project>
    <groupId>groupA</groupId>
    <artifactId>artifactA</artifactId>
    <version>1.2</version>
    <dependencies>
        <dependency>
            <groupId>groupB</groupId>
            <artifactId>artifactB</artifactId>
            <version>1.3</version>
        </dependency>
        <dependency>
            <groupId>groupB</groupId>
            <artifactId>artifactB</artifactId>
            <version>1.7</version>
        </dependency>
    </dependencies>
    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>groupB</groupId>
                    <artifactId>artifactB</artifactId>
                    <version>1.4</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        def transitiveModule = mavenHttpRepo.module("groupB", "artifactB", "1.4").publish()

        and:
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile 'groupA:artifactA:1.2' }
"""

        and:
        requestedModule.pom.expectGet()
        requestedModule.artifact.expectGet()
        transitiveModule.pom.expectGet()
        transitiveModule.artifact.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:unspecified") {
                module("groupA:artifactA:1.2") {
                    module("groupB:artifactB:1.4")
                }
            }
        }
    }
}
