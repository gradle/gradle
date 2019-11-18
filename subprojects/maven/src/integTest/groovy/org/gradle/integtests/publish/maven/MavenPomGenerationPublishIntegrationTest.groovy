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

package org.gradle.integtests.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Unroll

import static org.gradle.util.TextUtil.normaliseLineSeparators

// this spec documents the status quo, not a desired behavior
class MavenPomGenerationPublishIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // the OLD publish plugins work with the OLD deprecated Java plugin configuration (compile/runtime)
        executer.noDeprecationChecks()
        using m2 //uploadArchives leaks into local ~/.m2
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "how configuration of archive task affects generated POM"() {
        buildFile << """
apply plugin: "java"
apply plugin: "maven"

group = "org.gradle.test"
version = 1.9

jar {
    ${jarBaseName ? "baseName = '$jarBaseName'" : ""}
    ${jarVersion ? "version = '$jarVersion'" : ""}
    ${jarExtension ? "extension = '$jarExtension'" : ""}
    ${jarClassifier ? "classifier = '$jarClassifier'" : ""}
}

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${mavenRepo.uri}")
        }
    }
}
        """

        when:
        run "uploadArchives"

        then:
        def mavenModule = mavenRepo.module("org.gradle.test", pomArtifactId, pomVersion)
        def pom = mavenModule.parsedPom
        pom.groupId == "org.gradle.test"
        pom.artifactId == pomArtifactId
        pom.version == pomVersion
        pom.packaging == pomPackaging

        where:
        jarBaseName  | jarVersion | jarExtension | jarClassifier | pomArtifactId | pomVersion | pomPackaging
        "myBaseName" | "2.3"      | "jar"        | null          | "myBaseName"  | "1.9"      | null
        "myBaseName" | "2.3"      | "war"        | null          | "myBaseName"  | "1.9"      | "war"
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "how configuration of mavenDeployer.pom object affects generated POM"() {
        buildFile << """
apply plugin: "java"
apply plugin: "maven"

group = "org.gradle.test"
version = 1.9

jar {
    baseName = "jarBaseName"
    version = "2.3"
    extension = "war"
}

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${mavenRepo.uri}")
            ${deployerGroupId ? "pom.groupId = '$deployerGroupId'" : ""}
            ${deployerArtifactId ? "pom.artifactId = '$deployerArtifactId'" : ""}
            ${deployerVersion ? "pom.version = '$deployerVersion'" : ""}
            ${deployerPackaging ? "pom.packaging = '$deployerPackaging'" : ""}
        }
    }
}
        """

        when:
        run "uploadArchives"

        then:
        def mavenModule = mavenRepo.module(pomGroupId, pomArtifactId, pomVersion)
        def pom = mavenModule.parsedPom
        pom.groupId == pomGroupId
        pom.artifactId == pomArtifactId
        pom.version == pomVersion
        pom.packaging == pomPackaging

        where:
        deployerGroupId  | deployerArtifactId   | deployerVersion | deployerPackaging | pomGroupId       | pomArtifactId        | pomVersion | pomPackaging
        "deployer.group" | "deployerArtifactId" | "2.7"           | "jar"             | "deployer.group" | "deployerArtifactId" | "2.7"      | "war"
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "configuration attributes have no influence on generated POM file"() {
        buildFile << """
apply plugin: "java"
apply plugin: "maven"

group = "org.gradle.test"
version = 1.1

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${mavenRepo.uri}")
            pom.artifactId = 'something'
        }
    }
}

def foo = Attribute.of('foo', String)
def baz = Attribute.of('baz', String)
dependencies {
    attributesSchema {
        attribute(foo)
        attribute(baz)
    }
}
configurations {
    compile {
        $attributes
    }
}

        """

        when:
        run "uploadArchives"

        then:
        def mavenModule = mavenRepo.module('org.gradle.test', 'something', '1.1')
        def pom = normaliseLineSeparators(mavenModule.pomFile.text)
        assert pom == normaliseLineSeparators('''<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.gradle.test</groupId>
  <artifactId>something</artifactId>
  <version>1.1</version>
</project>
''')

        where:
        attributes << [
            '', // no attributes
            'attributes.attribute(foo, "bar")', // single attribute
            'attributes { attribute(foo, "bar"); attribute(baz, "baz") }' // multiple attributes
        ]
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "attributes have no influence on transitive dependencies in POM file"() {
        file("settings.gradle") << 'include "b"'
        buildFile << """
apply plugin: "java"
apply plugin: "maven"

group = "org.gradle.test"
version = 1.1

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${mavenRepo.uri}")
            pom.artifactId = 'something'
        }
    }
}

def foo = Attribute.of('foo', String)
def baz = Attribute.of('baz', String)
dependencies {
    compile project(':b')
    attributesSchema {
        attribute(foo)
        attribute(baz)
    }
}

configurations {
    compile {
        $attributes
    }
}


        """

        file('b/build.gradle') << """
apply plugin: 'java'

group = 'org.gradle.test'
version = '1.2'

def foo = Attribute.of('foo', String)
def baz = Attribute.of('baz', String)
dependencies {
    attributesSchema {
        attribute(foo)
        attribute(baz)
    }
}

configurations {
    compile {
        $attributes
    }
}

"""

        when:
        run "uploadArchives"

        then:
        def mavenModule = mavenRepo.module('org.gradle.test', 'something', '1.1')
        def pom = normaliseLineSeparators(mavenModule.pomFile.text)
        assert pom == normaliseLineSeparators('''<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.gradle.test</groupId>
  <artifactId>something</artifactId>
  <version>1.1</version>
  <dependencies>
    <dependency>
      <groupId>org.gradle.test</groupId>
      <artifactId>b</artifactId>
      <version>1.2</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
''')

        where:
        attributes << [
            '', // no attributes
            'attributes.attribute(foo, "bar")', // single attribute
            'attributes { attribute(foo, "bar"); attribute(baz, "baz") }' // multiple attributes
        ]
    }


    @Unroll("'#gradleConfiguration' dependencies end up in '#mavenScope' scope with '#plugin' plugin")
    @ToBeFixedForInstantExecution
    def "maps dependencies in the correct Maven scope"() {
        file("settings.gradle") << 'include "b"'
        buildFile << """
apply plugin: "$plugin"
apply plugin: "maven"

group = "org.gradle.test"
version = 1.1

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${mavenRepo.uri}")
            pom.artifactId = 'something'
        }
    }
}

dependencies {
    $gradleConfiguration project(':b')
}

        """

        file('b/build.gradle') << """
apply plugin: 'java'

group = 'org.gradle.test'
version = '1.2'

"""

        when:
        run "uploadArchives"

        then:
        def mavenModule = mavenRepo.module('org.gradle.test', 'something', '1.1')
        def pom = normaliseLineSeparators(mavenModule.pomFile.text)
        assert pom == normaliseLineSeparators("""<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.gradle.test</groupId>
  <artifactId>something</artifactId>
  <version>1.1</version>
  <dependencies>
    <dependency>
      <groupId>org.gradle.test</groupId>
      <artifactId>b</artifactId>
      <version>1.2</version>
      <scope>$mavenScope</scope>
    </dependency>
  </dependencies>
</project>
""")

        where:
        plugin         | gradleConfiguration  | mavenScope
        'java'         | 'compile'            | 'compile'
        'java'         | 'implementation'     | 'runtime'
        'java'         | 'testCompile'        | 'test'
        'java'         | 'testImplementation' | 'test'

        'java-library' | 'api'                | 'compile'
        'java-library' | 'compile'            | 'compile'
        'java-library' | 'implementation'     | 'runtime'
        'java-library' | 'testCompile'        | 'test'
        'java-library' | 'testImplementation' | 'test'
    }
}
