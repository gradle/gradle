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

package org.gradle.integtests.fixtures

import org.gradle.test.fixtures.maven.MavenRemoteModule
import org.gradle.test.fixtures.maven.ModuleDescriptor
import org.gradle.test.fixtures.maven.publication.MavenReleasePublication
import org.gradle.test.fixtures.maven.publication.MavenSnapshotPublication
import spock.lang.Shared

import static org.hamcrest.Matchers.startsWith

abstract class AbstractMavenPublicationIntegrationSpec extends AbstractIntegrationSpec {

    @Shared
    protected MavenRemoteModule remoteModule

    @Shared
    protected MavenRemoteModule remoteSnapshotModule

    @Shared
    protected ModuleDescriptor moduleDescriptor

    @Shared
    protected ModuleDescriptor snapshotModuleDescriptor

    private final repoPathExpression = /.:\/\/(.*)/

    def setup() {
        moduleDescriptor = new ModuleDescriptor("org.gradle", 'gradle-publish', "1.45-RELEASE")
        snapshotModuleDescriptor = new ModuleDescriptor("org.gradle", 'gradle-publish', "1.45-SNAPSHOT")
        remoteModule = createMavenRemoteModule(moduleDescriptor)
        remoteSnapshotModule = createMavenRemoteModule(snapshotModuleDescriptor)
    }

    abstract String repositoryUrl()

    abstract String repositoryCredentials()

    abstract MavenRemoteModule createMavenRemoteModule(ModuleDescriptor moduleDescriptor)

    abstract String authFailureCause(ModuleDescriptor moduleDescriptor)


    String repositoryDsl() {
        """
        repositories {
            maven {
                url "${repositoryUrl()}"
                ${repositoryCredentials()}
            }
        }
        """
    }

    MavenReleasePublication getPublication() {
        def repositoryPath = (repositoryUrl() =~ repoPathExpression)[0][1]
        return new MavenReleasePublication(testDirectory.file(repositoryPath), moduleDescriptor)
    }


    MavenSnapshotPublication getSnapshotPublication() {
        def repositoryPath = (repositoryUrl() =~ repoPathExpression)[0][1]
        return new MavenSnapshotPublication(testDirectory.file(repositoryPath), snapshotModuleDescriptor)
    }

    def "should fail with an authentication error"() {
        setup:
        remoteModule.jar.expectUploadAccessDenied()
        buildFile << repositoryDsl()
        settingsFile << "rootProject.name = '${moduleDescriptor.moduleName}'"

        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = "${moduleDescriptor.organisation}"
    version = "${moduleDescriptor.revision}"

    publishing {
        ${repositoryDsl()}
        publications {
            pub(MavenPublication) {
                from components.java
            }
        }
    }
    """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishPubPublicationToMavenRepository'.")
        failure.assertThatCause(startsWith("Failed to publish publication 'pub' to repository 'maven'"))
                .assertHasCause(authFailureCause(moduleDescriptor))
    }


    def "can publish a simple maven release artifact"() {
        setup:
        remoteModule.expectReleasePublish()
        buildFile << repositoryDsl()

        when:
        settingsFile << "rootProject.name = '${moduleDescriptor.moduleName}'"

        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = "${moduleDescriptor.organisation}"
    version = "${moduleDescriptor.revision}"

    publishing {
        ${repositoryDsl()}
        publications {
            pub(MavenPublication) {
                from components.java
            }
        }
    }
    """

        then:
        succeeds 'publish'

        and:
        MavenReleasePublication publication = getPublication()
        publication.assertJavaPublication()
    }

    def "can publish a release with sources and javadoc"() {
        setup:
        remoteModule.jar.expectPublish()
        remoteModule.javaSource.expectPublish()
        remoteModule.javadoc.expectPublish()
        remoteModule.rootMetaData.expectDownload()
        remoteModule.rootMetaData.expectPublish()
        remoteModule.pomFile.expectPublish()

        when:
        settingsFile << "rootProject.name = '${moduleDescriptor.moduleName}'"

        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = "${moduleDescriptor.organisation}"
    version = "${moduleDescriptor.revision}"

    task sourceJar(type: Jar) {
        classifier = 'sources'
        from sourceSets.main.allJava
    }

    task javadocJar(type: Jar, dependsOn: javadoc) {
        classifier = 'javadoc'
        from javadoc.destinationDir
    }

    publishing {
        ${repositoryDsl()}
        publications {
            pub(MavenPublication) {
                from components.java
                artifact sourceJar
                artifact javadocJar
            }
        }
    }
    """

        then:
        succeeds 'publish'

        and:
        MavenReleasePublication publication = getPublication()
        publication.assertJavaPublicationWithSourceAndJavadoc()
    }

    def "can publish and re-publish a maven snapshot"() {
        remoteSnapshotModule.expectFirstTimeSnapshotPublish()
        when:
        settingsFile << "rootProject.name = '${snapshotModuleDescriptor.moduleName}'"

        buildFile << """
apply plugin: 'java'
apply plugin: 'maven-publish'

group = "${snapshotModuleDescriptor.organisation}"
version = "${snapshotModuleDescriptor.revision}"

publishing {
    ${repositoryDsl()}
    publications {
        pub(MavenPublication) {
            from components.java
        }
    }
}
"""

        then:
        succeeds 'publish'

        and:
        MavenSnapshotPublication publication = getSnapshotPublication()
        publication.assertJavaPublication()
        publication.assertLatestBuild(1)
        when:
//        server.resetExpectations()
        remoteSnapshotModule.expectSnapshotRePublish()

        then:
        succeeds('clean', 'publish')
        MavenSnapshotPublication secondPublication =  getSnapshotPublication()
        secondPublication.assertJavaPublication()
        secondPublication.assertLatestBuild(2)
    }

    def "should publish a snapshot with sources and javadoc"() {
        remoteSnapshotModule.metaData.expectDownload()
        remoteSnapshotModule.jar.expectPublish()
        remoteSnapshotModule.metaData.expectDownload()
        remoteSnapshotModule.metaData.expectPublish()
        remoteSnapshotModule.rootMetaData.expectDownload()
        remoteSnapshotModule.rootMetaData.expectPublish()
        remoteSnapshotModule.pomFile.expectPublish()
        remoteSnapshotModule.metaData.expectDownload()
        remoteSnapshotModule.metaData.expectSha1Download()
        remoteSnapshotModule.javaSource.expectPublish()
        remoteSnapshotModule.metaData.expectDownload()
        remoteSnapshotModule.metaData.expectSha1Download()
        remoteSnapshotModule.javadoc.expectPublish()

        when:
        settingsFile << "rootProject.name = '${snapshotModuleDescriptor.moduleName}'"

        buildFile << """

  apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = "${snapshotModuleDescriptor.organisation}"
    version = "${snapshotModuleDescriptor.revision}"

    task sourceJar(type: Jar) {
        classifier = 'sources'
        from sourceSets.main.allJava
    }

    task javadocJar(type: Jar, dependsOn: javadoc) {
        classifier = 'javadoc'
        from javadoc.destinationDir
    }


    publishing {
        ${repositoryDsl()}
        publications {
            pub(MavenPublication) {
                from components.java
                artifact sourceJar
                artifact javadocJar
            }
        }
    }
"""

        then:
        succeeds 'publish'

        and:
        MavenSnapshotPublication publication = getSnapshotPublication()
        publication.assertJavaPublicationWithSourceAndJavadoc()
    }
}