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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.Issue

@LeaksFileHandles
class ResolveCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    @Rule
    HttpServer server = new HttpServer()
    def mavenHttpRepo = new MavenHttpRepository(server, mavenRepo)
    def ivyHttpRepo = new IvyHttpRepository(server, new IvyFileRepository(file("ivy-repo")))

    def setup() {
        requireOwnGradleUserHomeDir()
        server.start()
    }

    def "can upgrade and downgrade Gradle version"() {
        given:
        mavenHttpRepo.module("test", "io", "1.4").publish()
        mavenHttpRepo.module("test", "lang", "2.4").publish()
        mavenHttpRepo.module("test", "lang", "2.6").publish()

        and:
        server.allowGetOrHead("/repo", mavenRepo.rootDir)

        and:
        buildFile << """
repositories {
    if (repositories.metaClass.respondsTo(repositories, 'maven')) {
        maven { url = "${mavenHttpRepo.uri}" }
    } else {
        mavenRepo urls: "${mavenHttpRepo.uri}"
    }
}

configurations {
    compile
}

dependencies {
    compile 'test:io:1.4'
    compile 'test:lang:2.+'
}

task check {
    doLast {
        assert configurations.compile*.name as Set == ['io-1.4.jar', 'lang-2.6.jar'] as Set
    }
}
"""

        expect:
        version previous withTasks 'check' run()
        version current withTasks 'check' run()
        version previous withTasks 'check' run()
    }

    @Issue("GRADLE-3153")
    def "can upgrade when ivy.xml contains namespaced extra info elements"() {
        given:
        def module = ivyHttpRepo.module("test", "io", "1.4").publish()
        module.ivyFile.text = """
<ivy-module version="1.0">
    <info module="io" organisation="test" publication="20080831111344" revision="1.4" status="release">
        <description homepage="http://some-thing/" />
        <ns0:properties__organization.logo xmlns:ns0="http://ant.apache.org/ivy/maven">http://www.apache.org/images/asf_logo_wide.gif</ns0:properties__organization.logo>
    </info>
    <configurations>
        <conf name="default" visibility="public"/>
    </configurations>
    <publications>
        <artifact conf="*" ext="jar" name="io" type="jar"/>
    </publications>
</ivy-module>"""

        and:
        module.allowAll()

        and:
        buildFile << """
repositories {
    if (gradle.gradleVersion == '${current.version.version}' || ${previous.fullySupportsIvyRepository}) {
        ivy { url = "${ivyHttpRepo.uri}" }
    } else {
        add(Class.forName('org.apache.ivy.plugins.resolver.URLResolver').newInstance()) {
            name = 'repo'
            addIvyPattern("${ivyHttpRepo.uri}/[organisation]/[module]/[revision]/ivy-[revision].xml")
            addArtifactPattern("${ivyHttpRepo.uri}/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]")
            descriptor = 'required'
            checkmodified = true
        }
    }
}

configurations {
    compile
}

dependencies {
    compile 'test:io:1.4'
}

task check {
    doLast {
        assert configurations.compile*.name as Set == ['io-1.4.jar'] as Set
    }
}
"""

        expect:
        version previous withTasks 'check' run()
        version current withTasks 'check' run()
        version current withTasks 'check' run()
    }
}
