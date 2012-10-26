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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import spock.lang.Specification

class DefaultRepositoryFactoryTest extends Specification {

    BaseRepositoryFactory baseRepositoryFactory = Mock(BaseRepositoryFactory)
    DefaultRepositoryFactory factory = new DefaultRepositoryFactory(baseRepositoryFactory)

    Action action(Closure closure) {
        new ClosureBackedAction(closure)
    }

    def "can configure flat dir by action"() {
        when:
        def repo = Mock(FlatDirectoryArtifactRepository)
        1 * baseRepositoryFactory.createFlatDirRepository() >> repo
        1 * repo.setDirs(['a', 'b'])
        1 * repo.setName('libs')
        _ * repo.getName() >> "libs"

        then:
        factory.flatDir(action { name = 'libs'; dirs = ['a', 'b'] }).is(repo)
    }

    def "can configure flat dir by map"() {
        when:
        def repo = Mock(FlatDirectoryArtifactRepository)
        1 * baseRepositoryFactory.createFlatDirRepository() >> repo
        1 * repo.setDirs(['a', 'b'])
        1 * repo.setName('libs')
        _ * repo.getName() >> "libs"

        then:
        factory.flatDir([name: 'libs'] + [dirs: ['a', 'b']]).is(repo)
    }

    def "can configure flat dir by map, one dir"() {
        when:
        def repo = Mock(FlatDirectoryArtifactRepository)
        1 * baseRepositoryFactory.createFlatDirRepository() >> repo
        1 * repo.setDirs(['a'])
        1 * repo.setName('libs')
        _ * repo.getName() >> "libs"

        then:
        factory.flatDir([name: 'libs'] + [dirs: 'a']).is(repo)
    }

    public void testMavenCentralWithNoArgs() {
        when:
        MavenArtifactRepository repository = Mock(MavenArtifactRepository)
        1 * baseRepositoryFactory.createMavenCentralRepository() >> repository

        then:
        factory.mavenCentral().is(repository)
    }


    def testMavenCentralWithSingleUrl() {
        when:
        String testUrl2 = 'http://www.gradle2.org'
        def repository = Mock(MavenArtifactRepository)
        1 * baseRepositoryFactory.createMavenCentralRepository() >> repository
        1 * repository.setArtifactUrls([testUrl2])
        repository.getName() >> "name"

        then:
        assert factory.mavenCentral(artifactUrls: [testUrl2]).is(repository)
    }

    def testMavenCentralWithNameAndUrls() {
        when:
        String testUrl1 = 'http://www.gradle1.org'
        String testUrl2 = 'http://www.gradle2.org'
        String name = 'customName'

        MavenArtifactRepository repository = Mock(MavenArtifactRepository)

        baseRepositoryFactory.createMavenCentralRepository() >> repository
        1 * repository.setName(name)
        1 * repository.getName() >> name
        1 * repository.setArtifactUrls([testUrl1, testUrl2])

        then:
        factory.mavenCentral(name: name, artifactUrls: [testUrl1, testUrl2]).is(repository)
    }

    def testMavenLocalWithNoArgs() {
        when:
        MavenArtifactRepository repository = Mock(MavenArtifactRepository)
        1 * baseRepositoryFactory.createMavenLocalRepository() >> repository

        then:
        factory.mavenLocal().is(repository)
    }

    def testIvyWithAction() {
        when:
        def repo = Mock(IvyArtifactRepository)
        baseRepositoryFactory.createIvyRepository() >> repo

        1 * repo.setName("foo")

        then:
        factory.ivy(action { name = "foo" })
    }

    def testMavenWithAction() {
        when:
        def repo = Mock(MavenArtifactRepository)
        baseRepositoryFactory.createMavenRepository() >> repo

        1 * repo.setName("foo")

        then:
        factory.maven(action { name = "foo" })
    }

}
