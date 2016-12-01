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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArtifactDeclarationIntegrationTest extends AbstractIntegrationSpec {

    def "artifact file may have no extension"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            allprojects {
                configurationAttributesSchema {
                    attribute(Attribute.of('usage', String))
                }
                configurations { foo { attributes usage: 'for-compile' } }
            }
            project(':a') {
                artifacts {
                    foo file("foo")
                    foo file("foo.txt")
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.foo.artifacts.files.collect { it.name } == ["foo", "foo.txt"]
                        assert configurations.foo.artifacts.collect { it.file.name } == ["foo", "foo.txt"]
                        assert configurations.foo.artifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["foo::", "foo:txt:txt"]
                        assert configurations.foo.artifacts.collect { it.classifier } == [null, null]
                    }
                }
            }
            project(':b') {
                dependencies {
                    foo project(':a')
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.foo.incoming.artifacts.collect { it.file.name } == ["foo", "foo.txt"]
                        assert configurations.foo.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == ["foo", "foo.txt"]
                        assert configurations.foo.resolvedConfiguration.resolvedArtifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["foo::", "foo:txt:txt"]
                        assert configurations.foo.resolvedConfiguration.resolvedArtifacts.collect { it.classifier } == [null, null]
                    }
                }
            }
        """

        expect:
        succeeds "checkArtifacts"
    }

    def "can define artifact using file and configure other properties using a map or closure or action"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            allprojects {
                configurationAttributesSchema {
                    attribute(Attribute.of('usage', String))
                }
                configurations { foo { attributes usage: 'for-compile' } }
            }
            project(':a') {
                artifacts {
                    foo file: file("a"), name: "thing-a", type: "report", extension: "txt", classifier: "report"
                    foo file("b"), {
                        name = "thing-b"
                        type = "report"
                        extension = "txt"
                        classifier = "report"
                    }
                    add('foo', file("c"), {
                        name = "thing-c"
                        type = "report"
                        extension = ""
                        classifier = "report"
                    })
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.foo.artifacts.files.collect { it.name } == ["a", "b", "c"]
                        assert configurations.foo.artifacts.collect { it.file.name } == ["a", "b", "c"]
                        assert configurations.foo.artifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["thing-a:txt:report", "thing-b:txt:report", "thing-c::report"]
                        assert configurations.foo.artifacts.collect { it.classifier } == ["report", "report", "report"]
                    }
                }
            }
            project(':b') {
                dependencies {
                    foo project(':a')
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.foo.incoming.artifacts.collect { it.file.name } == ["a", "b", "c"]
                        assert configurations.foo.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == ["a", "b", "c"]
                        assert configurations.foo.resolvedConfiguration.resolvedArtifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["thing-a:txt:report", "thing-b:txt:report", "thing-c::report"]
                        assert configurations.foo.resolvedConfiguration.resolvedArtifacts.collect { it.classifier } == ["report", "report", "report"]
                    }
                }
            }
        """

        expect:
        succeeds("checkArtifacts")
    }
}
