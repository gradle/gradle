/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r47

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject

@ToolingApiVersion(">=4.7 <5.0")
@TargetGradleVersion(">=4.7 <5.0")
class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification {

    @ToolingApiVersion(">=4.7")
    @TargetGradleVersion(">=4.7")
    def "has empty source dirs for project without java plugin"() {

        projectDir.create {
            src {
                main {
                    java {}
                    resources {}
                }
                test {
                    java {}
                    resources {}
                }
            }
        }

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        IdeaModule module = project.children[0]
        IdeaContentRoot root = module.contentRoots[0]

        then:
        root.sourceDirectories.empty
        root.resourceDirectories.empty
        root.testDirectories.empty
        root.testResourceDirectories.empty
    }

    def "custom source sets are not added as source directories by default"() {

        buildFile.text = '''
apply plugin: 'java'

sourceSets {
    main {
        java { srcDirs = ['mainSources'] }
        resources { srcDirs = ['mainResources'] }
    }

    foo {
        java { srcDirs = ['fooSources'] }
        resources { srcDirs = ['fooResources'] }
    }

    test {
        java { srcDirs = ['testSources'] }
        resources { srcDirs = ['testResources'] }
    }

}
'''

        projectDir.create {
            mainSources {}
            mainResources {}
            fooSources {}
            fooResources {}
            testSources {}
            testResources {}
        }

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        IdeaModule module = project.children[0]
        IdeaContentRoot root = module.contentRoots[0]

        then:
        root.sourceDirectories.size() == 2
        root.sourceDirectories.any { it.directory == file('mainSources')}
        root.sourceDirectories.any { it.directory == file('mainResources')}
        root.resourceDirectories.size() == 1
        root.resourceDirectories[0].directory == file('mainResources')
        root.testDirectories.size() == 2
        root.testDirectories.any { it.directory == file('testSources')}
        root.testDirectories.any { it.directory == file('testResources')}
        root.testResourceDirectories.size() == 1
        root.testResourceDirectories[0].directory == file('testResources')
    }

    def "accepts source directories of custom source sets as source directories"() {

        buildFile.text = '''
apply plugin: 'idea'
apply plugin: 'java'

sourceSets {
    main {
        java { srcDirs = ['mainSources'] }
        resources { srcDirs = ['mainResources'] }
    }

    foo {
        java { srcDirs = ['fooSources'] }
        resources { srcDirs = ['fooResources'] }
    }

    test {
        java { srcDirs = ['testSources'] }
        resources { srcDirs = ['testResources'] }
    }

}

idea.module {
    sourceDirs += sourceSets.foo.java.srcDirs
    resourceDirs += sourceSets.foo.resources.srcDirs
}
'''

        projectDir.create {
            mainSources {}
            mainResources {}
            fooSources {}
            fooResources {}
            testSources {}
            testResources {}
        }

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        IdeaModule module = project.children[0]
        IdeaContentRoot root = module.contentRoots[0]

        then:
        root.sourceDirectories.size() == 3
        root.sourceDirectories.any { it.directory == file('mainSources')}
        root.sourceDirectories.any { it.directory == file('fooSources')}
        root.sourceDirectories.any { it.directory == file('mainResources')}
        root.resourceDirectories.size() == 2
        root.resourceDirectories.any { it.directory == file('mainResources')}
        root.resourceDirectories.any { it.directory == file('fooResources')}
        root.testDirectories.size() == 2
        root.testDirectories.any { it.directory == file('testSources')}
        root.testDirectories.any { it.directory == file('testResources')}
        root.testResourceDirectories.size() == 1
        root.testResourceDirectories[0].directory == file('testResources')
    }

    @TargetGradleVersion(">=2.6 <4.7")
    def "older Gradle version throw UnsupportedMethodException when querying resources and test resources"() {
        given:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        IdeaModule module = project.children[0]
        IdeaContentRoot root = module.contentRoots[0]

        when:
        root.getResourceDirectories()

        then:
        thrown(UnsupportedMethodException)

        when:
        root.getTestResourceDirectories()

        then:
        thrown(UnsupportedMethodException)
    }
}
