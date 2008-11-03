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

package org.gradle.api.internal.dependencies.maven.deploy

import java.lang.reflect.Proxy
import org.gradle.api.dependencies.maven.MavenPom
import org.gradle.api.dependencies.maven.PublishFilter
import org.gradle.api.internal.dependencies.maven.deploy.ArtifactPomContainer
import org.gradle.api.internal.dependencies.maven.deploy.BaseMavenUploader
import org.gradle.api.internal.dependencies.maven.deploy.groovy.DefaultGroovyMavenUploader
import org.gradle.util.JUnit4GroovyMockery
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Factory
import org.hamcrest.Matcher
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.internal.dependencies.maven.DefaultMavenPom


/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock.class)
class DefaultGroovyMavenUploaderTest extends BaseMavenUploaderTest {
    private DefaultGroovyMavenUploader defaultMavenUploader;

    protected BaseMavenUploader createMavenUploader() {
        defaultMavenUploader = new DefaultGroovyMavenUploader(TEST_NAME, artifactPomContainerMock, mavenPomFactoryMock, dependencyManagerMock)
    }

    JUnit4GroovyMockery groovyContext = new JUnit4GroovyMockery()

    @Before
    void setUp() {
        super.setUp();
    }

    @Test
    void addFilterWithClosure() {
        artifactPomContainerMock = groovyContext.mock(ArtifactPomContainer)
        defaultMavenUploader.artifactPomContainer = artifactPomContainerMock
        Closure closureFilter = { }
        groovyContext.checking {
            one(artifactPomContainerMock).addArtifactPom(withParam(ArtifactPomMatcher.equalsArtifactPom(TEST_NAME, pomMock, closureFilter)));
        }
        defaultMavenUploader.addFilter(TEST_NAME, closureFilter)
    }

    @Test
    void filterWithClosure() {
        artifactPomContainerMock = groovyContext.mock(ArtifactPomContainer)
        ArtifactPom artifactPomMock = groovyContext.mock(ArtifactPom)
        defaultMavenUploader.artifactPomContainer = artifactPomContainerMock
        Closure closureFilter = { }
        groovyContext.checking {
            one(artifactPomContainerMock).getDefaultArtifactPom(); will(returnValue(artifactPomMock))
            one(artifactPomMock).setFilter(withParam(FilterMatcher.equalsFilter(closureFilter)))
        }
        defaultMavenUploader.filter(closureFilter)
    }

    @Test
    void pomWithClosure() {
        String testGroup = "testGroup"
        artifactPomContainerMock = groovyContext.mock(ArtifactPomContainer)
        ArtifactPom artifactPomMock = groovyContext.mock(ArtifactPom)
        MavenPom mavenPomMock = groovyContext.mock(MavenPom)
        defaultMavenUploader.artifactPomContainer = artifactPomContainerMock
        groovyContext.checking {
            allowing(artifactPomContainerMock).getArtifactPom(TEST_NAME); will(returnValue(artifactPomMock))
            allowing(artifactPomMock).getPom(); will(returnValue(mavenPomMock))
            one(mavenPomMock).setGroupId(testGroup)
        }
        defaultMavenUploader.pom(TEST_NAME) {
            groupId = testGroup
        }
    }

    @Test
    void defaultPomWithClosure() {
        String testGroup = "testGroup"
        artifactPomContainerMock = groovyContext.mock(ArtifactPomContainer)
        ArtifactPom artifactPomMock = groovyContext.mock(ArtifactPom)
        MavenPom mavenPomMock = groovyContext.mock(MavenPom)
        defaultMavenUploader.artifactPomContainer = artifactPomContainerMock
        groovyContext.checking {
            allowing(artifactPomContainerMock).getDefaultArtifactPom(); will(returnValue(artifactPomMock))
            allowing(artifactPomMock).getPom(); will(returnValue(mavenPomMock))
            one(mavenPomMock).setGroupId(testGroup)
        }
        defaultMavenUploader.pom {
            groupId = testGroup
        }
    }

    @Test
    void repositoryBuilder() {
        checkRepositoryBuilder(DefaultGroovyMavenUploader.REPOSITORY_BUILDER)
    }

    @Test
    void snapshotRepositoryBuilder() {
        checkRepositoryBuilder(DefaultGroovyMavenUploader.SNAPSHOT_REPOSITORY_BUILDER)
    }


    void checkRepositoryBuilder(String repositoryName) {
        String testUrl = 'testUrl'
        String testProxyHost = 'hans'
        String testUserName = 'userId'
        String testSnapshotUpdatePolicy = 'always'
        String testReleaseUpdatePolicy = 'never'
        defaultMavenUploader."$repositoryName"(url: testUrl) {
            authentication(userName: testUserName)
            proxy(host: testProxyHost)
            releases(updatePolicy: testReleaseUpdatePolicy)
            snapshots(updatePolicy: testSnapshotUpdatePolicy)
        }
        assertEquals(testUrl, defaultMavenUploader."$repositoryName".url)
        assertEquals(testUserName, defaultMavenUploader."$repositoryName".authentication.userName)
        assertEquals(testProxyHost, defaultMavenUploader."$repositoryName".proxy.host)
        assertEquals(testReleaseUpdatePolicy, defaultMavenUploader."$repositoryName".releases.updatePolicy)
        assertEquals(testSnapshotUpdatePolicy, defaultMavenUploader."$repositoryName".snapshots.updatePolicy)
    }

}

public class ArtifactPomMatcher extends BaseMatcher {
    String name
    MavenPom mavenPom
    Closure filter

    public void describeTo(Description description) {
        description.appendText("matching artifactPom");
    }

    public boolean matches(Object actual) {
        ArtifactPom actualArtifactPom = (ArtifactPom) actual;
        return actualArtifactPom.getName().equals(name) &&
                actualArtifactPom.getPom() == mavenPom &&
                getClosureFromProxy(actualArtifactPom.getFilter()) == filter;
    }

    private Closure getClosureFromProxy(PublishFilter filter) {
        Proxy.getInvocationHandler(filter).delegate
    }


    @Factory
    public static Matcher<DefaultArtifactPom> equalsArtifactPom(String name, MavenPom mavenPom, Closure filter) {
        return new ArtifactPomMatcher(name: name, mavenPom: mavenPom, filter: filter);
    }

}

public class FilterMatcher extends BaseMatcher {
    Closure filter

    public void describeTo(Description description) {
        description.appendText("matching filter");
    }

    public boolean matches(Object actual) {
        return getClosureFromProxy(actual) == filter;
    }

    private Closure getClosureFromProxy(PublishFilter filter) {
        Proxy.getInvocationHandler(filter).delegate
    }


    @Factory
    public static Matcher<PublishFilter> equalsFilter(Closure filter) {
        return new FilterMatcher(filter: filter);
    }

}



