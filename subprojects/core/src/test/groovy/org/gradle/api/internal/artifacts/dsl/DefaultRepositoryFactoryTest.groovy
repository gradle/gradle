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

import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME
import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME

class DefaultRepositoryFactoryTest extends Specification {

    BaseRepositoryFactory baseRepositoryFactory = Mock(BaseRepositoryFactory)
    DefaultRepositoryFactory factory = new DefaultRepositoryFactory(baseRepositoryFactory)

    Action action(Closure closure) {
        new ClosureBackedAction(closure)
    }

    def "flat dir repos are given a default name"() {
        when:
        def repo = Mock(FlatDirectoryArtifactRepository)
        1 * baseRepositoryFactory.createFlatDirRepository() >> repo
        1 * repo.setName(DefaultRepositoryFactory.FLAT_DIR_DEFAULT_NAME)

        then:
        factory.flatDir(action { }).is(repo)
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
        1 * repository.setName(DEFAULT_MAVEN_CENTRAL_REPO_NAME)

        then:
        factory.mavenCentral().is(repository)
    }

    def testMavenLocalWithNoArgs() {
        when:
        MavenArtifactRepository repository = Mock(MavenArtifactRepository)
        1 * baseRepositoryFactory.createMavenLocalRepository() >> repository
        1 * repository.setName(DEFAULT_MAVEN_LOCAL_REPO_NAME)

        then:
        factory.mavenLocal().is(repository)
    }

    def "ivy repos are assigned a default name"() {
        when:
        def repo = Mock(IvyArtifactRepository)
        baseRepositoryFactory.createIvyRepository() >> repo
        1 * repo.setName("ivy")

        then:
        factory.ivy(action { })
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
