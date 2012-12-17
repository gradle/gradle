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

package org.gradle.integtests.resolve.caching

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

public class CachedChangingModulesIntegrationTest extends AbstractDependencyResolutionTest {

    def "can cache and refresh unique versioned maven artifacts with a classifier"() {
        given:
        server.start()
        def repo = mavenHttpRepo("repo")
        def module = repo.module("group", "projectA", "1.0-SNAPSHOT")
        def sourceArtifact = module.artifact(classifier: "source")

        module.publish()
        buildFile << """
        repositories {
            maven {
                name 'repo'
                url '${repo.uri}'
            }
        }
        configurations {
            compile
        }

        configurations.all {
            resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
        }

        dependencies {
            compile 'group:projectA:1.0-SNAPSHOT:source'
        }

        task retrieve(type: Sync) {
            into 'libs'
            from configurations.compile
        }
        """

        when:
        module.expectPomGet()
        sourceArtifact.expectGet()
        module.expectMetaDataGet()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        module.expectMetaDataGet()
        sourceArtifact.expectHead()
        module.expectPomHead()
        then:
        run 'retrieve'

        when:
        module.publishWithChangedContent()
        server.resetExpectations()

        module.expectMetaDataGet()
        module.expectPomSha1Get()
        module.expectPomHead()
        module.expectPomGet()
        sourceArtifact.expectHead()
        sourceArtifact.expectGet()
        sourceArtifact.sha1.expectGet()
        then:
        run 'retrieve'

        when:
        module.publishWithChangedContent()
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
    }

    def "can cache and refresh non unique versioned maven artifacts with a classifier"() {
        given:
        server.start()
        def repo = mavenHttpRepo("repo")
        def module = repo.module("group", "projectA", "1.0-SNAPSHOT").withNonUniqueSnapshots()
        def sourceArtifact = module.artifact(classifier: "source")

        module.publish()
        buildFile << """
        repositories {
            maven {
                name 'repo'
                url '${repo.uri}'
            }
        }
        configurations {
            compile
        }

        configurations.all {
            resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
        }

        dependencies {
            compile 'group:projectA:1.0-SNAPSHOT:source'
        }

        task retrieve(type: Sync) {
            into 'libs'
            from configurations.compile
        }
        """

        when:
        module.expectPomGet()
        sourceArtifact.expectGet()
        module.expectMetaDataGetMissing()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        module.expectMetaDataGetMissing()
        sourceArtifact.expectHead()
        module.expectPomHead()
        then:
        run 'retrieve'

        when:
        module.publishWithChangedContent()
        server.resetExpectations()

        module.expectMetaDataGetMissing()
        module.expectPomSha1Get()
        module.expectPomHead()
        module.expectPomGet()
        sourceArtifact.expectHead()
        sourceArtifact.expectGet()
        sourceArtifact.sha1.expectGet()
        then:
        run 'retrieve'

        when:
        module.publishWithChangedContent()
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
    }

    def "can cache and refresh ivy changing artifacts with a classifier"() {
        given:
        server.start()
        def repo = ivyHttpRepo("repo")
        def module = repo.module("group", "projectA", "1.0")
        module.artifact(classifier: "source")

        module.publish()
        buildFile << """
          repositories {
              ivy {
                  name 'repo'
                  url '${repo.uri}'
              }
          }
          configurations {
              compile
          }

          configurations.all {
              resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
          }

          dependencies {
                compile group: "group", name: "projectA", version: "1.0", classifier: "source", changing: true
          }

          task retrieve(type: Sync) {
              into 'libs'
              from configurations.compile
          }
          """
        when:
        module.expectIvyGet()
        module.expectArtifactGet(name: "projectA", classifier: "source")

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        module.expectIvyHead()
        module.expectArtifactHead(name: "projectA", classifier: 'source')
        then:
        run 'retrieve'

        when:
        module.publishWithChangedContent()
        server.resetExpectations()
        module.expectIvyHead()
        module.expectArtifactHead(name: "projectA", classifier: 'source')

        module.expectIvySha1Get()
        module.expectIvyGet()
        module.expectArtifactGet(name: "projectA", classifier: 'source')
        module.expectArtifactSha1Get(name: "projectA", classifier: 'source')

        then:
        run 'retrieve'

        when:
        module.publishWithChangedContent()
        server.resetExpectations()
        then:
        executer.withArgument("--offline")
        run 'retrieve'
    }
}
