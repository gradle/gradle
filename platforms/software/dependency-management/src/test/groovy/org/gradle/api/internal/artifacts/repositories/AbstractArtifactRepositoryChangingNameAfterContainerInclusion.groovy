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

package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class AbstractArtifactRepositoryChangingNameAfterContainerInclusion extends AbstractProjectBuilderSpec {

    def "cannot change the name of the repository after it has been added to a container"() {
        def repo = new TestRepo(name: "name")

        given:
        project.repositories.add(repo)

        when:
        repo.name = "changed"

        then:
        IllegalStateException e = thrown()
        e.message == 'The name of an ArtifactRepository cannot be changed after it has been added to a repository container. You should set the name when creating the repository.'
    }

    class TestRepo extends AbstractArtifactRepository {
        def TestRepo() {
            super(null, new VersionParser())
        }
    }
}
