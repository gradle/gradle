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

package org.gradle.integtests.publish.ivy


import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class IvySingleProjectPublishIntegrationTest extends AbstractLegacyIvyPublishTest {
    def setup() {
        configureUploadTask("publish")
    }

    @ToBeFixedForConfigurationCache
    def "publish multiple artifacts in single configuration"() {
        settingsFile << "rootProject.name = 'publishTest'"
        file("file1") << "some content"
        file("file2") << "other content"

        buildFile << """
apply plugin: "base"

group = "org.gradle.test"
version = 1.9

configurations { publish }

task jar1(type: Jar) {
    archiveBaseName = "jar1"
    from "file1"
}

task jar2(type: Jar) {
    archiveBaseName = "jar2"
    from "file2"
}

artifacts {
    publish jar1, jar2
}

uploadPublish {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
        }
    }
}
        """

        when:
        run "uploadPublish"

        then:
        def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")
        ivyModule.assertArtifactsPublished("ivy-1.9.xml", "jar1-1.9.jar", "jar2-1.9.jar")
        ivyModule.moduleDir.file("jar1-1.9.jar").bytes == file("build/libs/jar1-1.9.jar").bytes
        ivyModule.moduleDir.file("jar2-1.9.jar").bytes == file("build/libs/jar2-1.9.jar").bytes

        and:
        def ivyDescriptor = ivyModule.parsedIvy
        ivyDescriptor.expectArtifact("jar1").conf == ["archives", "publish"]
        ivyDescriptor.expectArtifact("jar2").conf == ["publish"]
    }

    @ToBeFixedForConfigurationCache
    def "publish classified artifact"() {
        settingsFile << "rootProject.name = 'publishTest'"
        file("file1") << "some content"

        buildFile << """
apply plugin: "base"

group = "org.gradle.test"
version = 1.9

configurations { publish }

task jar1(type: Jar) {
    archiveBaseName = "jar1"
    archiveClassifier = "classy"
    from "file1"
}

artifacts {
    publish jar1
}

uploadPublish {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
        }
    }
}
        """

        when:
        run "uploadPublish"

        then:
        def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")
        ivyModule.assertArtifactsPublished("ivy-1.9.xml", "jar1-1.9-classy.jar")

        and:
        def ivyDescriptor = ivyModule.parsedIvy
        ivyDescriptor.expectArtifact("jar1").classifier == "classy"
    }

    @ToBeFixedForConfigurationCache
    def "publish multiple artifacts in separate configurations"() {
        configureUploadTask('publish1')
        configureUploadTask('publish2')
        file("settings.gradle") << "rootProject.name = 'publishTest'"
        file("file1") << "some content"
        file("file2") << "other content"

        buildFile << """
apply plugin: "base"

group = "org.gradle.test"
version = 1.9

configurations { publish1; publish2 }

task jar1(type: Jar) {
    archiveBaseName = "jar1"
    from "file1"
}

task jar2(type: Jar) {
    archiveBaseName = "jar2"
    from "file2"
}

artifacts {
    publish1 jar1
    publish2 jar2
}

tasks.withType(Upload) {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
        }
    }
}
        """

        when:
        run "uploadPublish$n"

        then:
        def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")
        ivyModule.assertArtifactsPublished("ivy-1.9.xml", "jar$n-1.9.jar")
        ivyModule.moduleDir.file("jar$n-1.9.jar").bytes == file("build/libs/jar$n-1.9.jar").bytes

        and:
        def ivyDescriptor = ivyModule.parsedIvy
        ivyDescriptor.expectArtifact("jar$n").conf.contains("publish$n" as String)
        ivyDescriptor.expectArtifact("jar$n").conf.contains("archives") == onArchivesConfig

        where:
        n | onArchivesConfig
        1 | true
        2 | false
    }
}
