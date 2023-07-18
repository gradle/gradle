/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve.maven;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

class MavenDependencyManagementImportOrderTest extends AbstractIntegrationSpec {
	@Issue("https://github.com/gradle/gradle/issues/2212")
    @Requires(UnitTestPreconditions.Online)
	def "Verify that gradle resolves org.wildfly.swarm:undertow the same as maven"() {
		when:
		buildFile.text = """
${mavenCentralRepository()}
configurations {
	test
}
dependencies {
	test('org.wildfly.swarm:undertow:2017.5.0') {
		exclude module: 'javax.el-impl'
	}
}
task verifyUndertowVersion {
    def files = configurations.test
	doLast {
		def fileNames = files.collect { it.name }
		assert fileNames.contains('undertow-servlet-1.4.11.Final.jar')
		assert !fileNames.contains('undertow-servlet-1.2.9.Final.jar')
	}
}
		"""

		then:
		succeeds 'verifyUndertowVersion'
	}

	@Issue("https://github.com/gradle/gradle/issues/2212")
	def "Verify that if multiple boms declare the same dependency, the first bom wins"() {
		file("mavenRoot/group/level1/1/level1-1.pom").text = '''\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>group</groupId>
  <artifactId>level1</artifactId>
  <version>1</version>
  <packaging>jar</packaging>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>group</groupId>
        <artifactId>bom1</artifactId>
        <version>1</version>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>group</groupId>
        <artifactId>bom2</artifactId>
        <version>1</version>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>group</groupId>
      <artifactId>level2</artifactId>
    </dependency>
  </dependencies>
</project>'''

		file("mavenRoot/group/level1/1/level1-1.jar").text = 'foo'

		file("mavenRoot/group/bom1/1/bom1-1.pom").text = '''\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>group</groupId>
  <artifactId>bom1</artifactId>
  <version>1</version>
  <packaging>pom</packaging>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>group</groupId>
        <artifactId>level2</artifactId>
        <version>1</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>'''

		file("mavenRoot/group/bom2/1/bom2-1.pom").text = '''\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>group</groupId>
  <artifactId>bom2</artifactId>
  <version>1</version>
  <packaging>pom</packaging>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>group</groupId>
        <artifactId>level2</artifactId>
        <version>2</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>'''

		file("mavenRoot/group/level2/1/level2-1.pom").text = '''\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>group</groupId>
  <artifactId>level2</artifactId>
  <version>1</version>
  <packaging>jar</packaging>
</project>'''

		file("mavenRoot/group/level2/1/level2-1.jar").text = 'foo'

		file("mavenRoot/group/level2/2/level2-2.pom").text = '''\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>group</groupId>
  <artifactId>level2</artifactId>
  <version>1</version>
  <packaging>jar</packaging>
</project>'''

		file("mavenRoot/group/level2/2/level2-2.jar").text = 'foo'

		when:
		buildFile.text = '''
repositories {
	maven {
		url uri(file('mavenRoot'))
	}
}
configurations {
	test
}
dependencies {
	test 'group:level1:1'
}
task verifyVersion {
    def files = configurations.test
	doLast {
		def fileNames = files.collect { it.name }
		assert fileNames.contains('level1-1.jar')
		assert fileNames.contains('level2-1.jar')
		assert !fileNames.contains('level2-2.jar')
	}
}
		'''

		then:
		succeeds 'verifyVersion'
	}
}
