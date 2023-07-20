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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

class CachedMissingModulesIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def "caches missing module when module found in another repository"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def moduleInRepo1 = repo1.module("group", "projectA", "1.2")
        def moduleInRepo2 = repo2.module('group', 'projectA', '1.2').publish()

        buildFile << """
repositories {
    ivy { url "${repo1.uri}"}
    ivy { url "${repo2.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
task showMissing {
    def missing = configurations.missing
    doLast { println missing.files }
}
"""

        when:
        moduleInRepo1.ivy.expectGetMissing()
        moduleInRepo2.ivy.expectGet()
        moduleInRepo2.jar.expectGet()

        then:
        succeeds("showMissing")

        when:
        server.resetExpectations() // Missing status in repo1 is cached

        then:
        succeeds('showMissing')
    }

    def "caches missing changing module when module found in another repository"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def moduleInRepo1 = repo1.module("group", "projectA", "1.2")
        def moduleInRepo2 = repo2.module('group', 'projectA', '1.2').publish()

        buildFile << """
repositories {
    ivy { url "${repo1.uri}"}
    ivy { url "${repo2.uri}"}
}
configurations {
    missing
    all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}
dependencies {
    missing group: 'group', name: 'projectA', version: '1.2', changing: true
}
task showMissing {
    def missing = configurations.missing
    doLast { println missing.files }
}
"""

        when:
        moduleInRepo1.ivy.expectGetMissing()
        moduleInRepo2.ivy.expectGet()
        moduleInRepo2.jar.expectGet()

        then:
        succeeds("showMissing")

        when:
        server.resetExpectations() // Missing status in repo1 is cached
        moduleInRepo2.ivy.expectHead()
        moduleInRepo2.jar.expectHead()

        then:
        succeeds('showMissing')
    }

    def "checks for missing modules in each repository when run with --refresh-dependencies"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def moduleInRepo1 = repo1.module("group", "projectA", "1.2")
        def moduleInRepo2 = repo2.module('group', 'projectA', '1.2').publish()

        buildFile << """
repositories {
    ivy { url "${repo1.uri}"}
    ivy { url "${repo2.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
task showMissing {
    def missing = configurations.missing
    doLast { println missing.files }
}
"""

        when:
        moduleInRepo1.ivy.expectGetMissing()
        moduleInRepo2.ivy.expectGet()
        moduleInRepo2.jar.expectGet()

        then:
        succeeds("showMissing")

        when:
        moduleInRepo1.publish()
        server.resetExpectations()
        then:
        succeeds("showMissing")

        when:
        executer.withArgument("--refresh-dependencies")
        moduleInRepo1.ivy.expectHead()
        moduleInRepo1.ivy.sha1.expectGet()
        moduleInRepo1.jar.expectHead()
        moduleInRepo1.jar.sha1.expectGet()

        then:
        succeeds("showMissing")
    }

    def "cached empty version list is ignored when no module for dynamic version is available in any repo"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        buildFile << """
            repositories {
                maven {
                    name 'repo1'
                    url '${repo1.uri}'
                }
                maven {
                    name 'repo2'
                    url '${repo2.uri}'
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
            }
            configurations { compile }
            dependencies {
                compile 'group:projectA:latest.integration'
            }

            task retrieve(type: Sync) {
                into 'libs'
                from configurations.compile
            }
            """

        when:
        def repo1MetaData = repo1.getModuleMetaData("group", "projectA")
        def repo1DirList = repo1.directory("group", "projectA")
        def repo2MetaData = repo2.getModuleMetaData("group", "projectA")
        def repo2DirLib = repo2.directory("group", "projectA")
        def repo2Module = repo2.module("group", "projectA", "1.0")

        repo1MetaData.expectGetMissing()
        repo2MetaData.expectGetMissing()
        repo2DirLib.expectGet()

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause("""Could not find any matches for group:projectA:latest.integration as no versions of group:projectA are available.
Searched in the following locations:
  - ${repo1MetaData.uri}
  - ${repo2MetaData.uri}
  - ${repo2DirLib.uri}
Required by:
""")

        when:
        server.resetExpectations()
        repo1MetaData.expectGetMissing()
        repo2MetaData.expectGetMissing()
        repo2DirLib.expectGet()

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause("""Could not find any matches for group:projectA:latest.integration as no versions of group:projectA are available.
Searched in the following locations:
  - ${repo1MetaData.uri}
  - ${repo2MetaData.uri}
  - ${repo2DirLib.uri}
Required by:
""")

        when:
        server.resetExpectations()
        repo2Module.publish()
        repo1MetaData.expectGetMissing()
        repo2MetaData.expectGet()
        repo2Module.pom.expectGet()
        repo2Module.artifact.expectGet()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()

        then:
        run 'retrieve'
    }

    def "previously cached empty version list is ignored when no result can be found"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        buildFile << """
            repositories {
                maven {
                    name 'repo1'
                    url '${repo1.uri}'
                }
                maven {
                    name 'repo2'
                    url '${repo2.uri}'
                }
            }
            configurations {
                compile
            }
            dependencies {
                compile "group:projectA:\$depVersion"
            }

            task retrieve(type: Sync) {
                into 'libs'
                from configurations.compile
            }
            """

        when:
        def repo1MetaData = repo1.getModuleMetaData("group", "projectA")
        def repo1DirList = repo1.directory("group", "projectA")
        def repo1Module = repo1.module("group", "projectA", "2.0")
        def repo2MetaData = repo2.getModuleMetaData("group", "projectA")
        def repo2Module = repo2.module("group", "projectA", "1.0").publish()

        repo1MetaData.expectGetMissing()
        repo2MetaData.expectGet()
        repo2Module.pom.expectGet()
        repo2Module.artifact.expectGet()

        then:
        succeeds 'retrieve', '-PdepVersion=1.+'

        when:
        server.resetExpectations()
        repo1Module.publish()
        repo1MetaData.expectGet()
        repo1Module.pom.expectGet()
        repo1Module.artifact.expectGet()
        repo2MetaData.expectHead()

        then:
        succeeds 'retrieve', '-PdepVersion=2.+'

    }

    def "needs explicit refresh of dependencies when dynamic version appears in a previously empty repository"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        buildFile << """
            repositories {
                maven {
                    name 'repo1'
                    url '${repo1.uri}'
                }
                maven {
                    name 'repo2'
                    url '${repo2.uri}'
                }
            }
            configurations {
                all {
                    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
                }
                compile
            }
            dependencies {
                compile "group:projectA:1.+"
            }

            task retrieve(type: Sync) {
                into 'libs'
                from configurations.compile
            }
            """

        when:
        def repo1MetaData = repo1.getModuleMetaData("group", "projectA")
        def repo1DirList = repo1.directory("group", "projectA")
        def repo1Module = repo1.module("group", "projectA", "1.1")
        def repo2MetaData = repo2.getModuleMetaData("group", "projectA")
        def repo2Module = repo2.module("group", "projectA", "1.0").publish()
        def repo2Module2 = repo2.module("group", "projectA", "1.1")

        repo1MetaData.expectGetMissing()
        repo2MetaData.expectGet()
        repo2Module.pom.expectGet()
        repo2Module.artifact.expectGet()

        then:
        succeeds 'retrieve'

        when:
        server.resetExpectations()
        repo1Module.publish()
        repo2MetaData.expectHead()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants('projectA-1.0.jar')

        when:
        server.resetExpectations()
        repo1Module.publish()
        repo1MetaData.expectGet()
        repo1Module.pom.expectGet()
        repo1Module.artifact.expectGet()
        repo2MetaData.expectHead()
        repo2Module.pom.expectHead()

        then:
        succeeds 'retrieve', '--refresh-dependencies'
        file('libs').assertHasDescendants('projectA-1.1.jar')

    }

    def "cached missing module is ignored if module is not available in any repo"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo1Module = repo1.module("group", "projectA", "1.0")
        def repo1Artifact = repo1Module.artifact

        def repo2 = mavenHttpRepo("repo2")
        def repo2Module = repo2.module("group", "projectA", "1.0")
        def repo2Artifact = repo2Module.artifact

        buildFile << """
    repositories {
        maven {
            name 'repo1'
            url '${repo1.uri}'
        }
        maven {
            name 'repo2'
            url '${repo2.uri}'
        }
    }
    configurations { compile }
    dependencies {
        compile 'group:projectA:1.0'
    }

    task retrieve(type: Sync) {
        into 'libs'
        from configurations.compile
    }
    """

        when:
        repo1Module.pom.expectGetMissing()
        repo2Module.pom.expectGetMissing()

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause("""Could not find group:projectA:1.0.
Searched in the following locations:
  - ${repo1Module.pom.uri}
  - ${repo2Module.pom.uri}
Required by:
""")

        when:
        repo1Module.pom.expectGetMissing()
        repo2Module.pom.expectGetMissing()

        then:
        fails 'retrieve'

        failure.assertHasCause("""Could not find group:projectA:1.0.
Searched in the following locations:
  - ${repo1Module.pom.uri}
  - ${repo2Module.pom.uri}
Required by:
""")

        when:
        server.resetExpectations()
        repo1Module.pom.expectGetMissing()
        repo2Module.publish()
        repo2Module.pom.expectGet()
        repo2Artifact.expectGet()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()

        then:
        run 'retrieve'
    }

    @ToBeFixedForConfigurationCache(because = "HTTP server interactions are different when CC is enabled")
    def "cached missing module is ignored when no module for dynamic version is available in any repo"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo1Module = repo1.module("group", "projectA", "1.0").publish()
        def repo2 = mavenHttpRepo("repo2")
        def repo2Module = repo2.module("group", "projectA", "1.0").publish()

        buildFile << """
    repositories {
        maven {
            name 'repo1'
            url '${repo1.uri}'
        }
        maven {
            name 'repo2'
            url '${repo2.uri}'
        }
    }
    configurations { conf1; conf2 }
    dependencies {
        conf1 'group:projectA:1.0'
        conf2 'group:projectA:1.+'
    }

    task cache {
        def conf1 = configurations.conf1
        doLast { println conf1.files }
    }
    task retrieve(type: Sync) {
        into 'libs'
        from configurations.conf2
    }
    """

        and:
        repo1Module.pom.expectGetMissing()
        repo2Module.pom.expectGetMissing()
        fails 'cache'
        failure.assertHasCause("Could not find group:projectA:1.0.")

        when:
        server.resetExpectations()
        repo1Module.rootMetaData.expectGet()
        repo2Module.rootMetaData.expectGet()
        repo1Module.pom.expectGetMissing()
        repo2Module.pom.expectGetMissing()

        then:
        fails 'retrieve'
        failure.assertHasCause("""Could not find any matches for group:projectA:1.+ as no versions of group:projectA are available.
Searched in the following locations:
  - ${repo1Module.rootMetaData.uri}
  - ${repo1Module.pom.uri}
  - ${repo2Module.rootMetaData.uri}
  - ${repo2Module.pom.uri}
Required by:
""")

        when:
        server.resetExpectations()
        repo1Module.rootMetaData.expectHead()
        repo1Module.pom.expectGetMissing()
        repo2Module.rootMetaData.expectHead()
        repo2Module.pom.expectGetMissing()

        then:
        fails 'retrieve'
        failure.assertHasCause("""Could not find any matches for group:projectA:1.+ as no versions of group:projectA are available.
Searched in the following locations:
  - ${repo1Module.rootMetaData.uri}
  - ${repo1Module.pom.uri}
  - ${repo2Module.rootMetaData.uri}
  - ${repo2Module.pom.uri}
Required by:
""")

        when:
        server.resetExpectations()
        repo1Module.pom.expectGetMissing()
        repo2Module.pom.expectGet()
        repo2Module.artifact.expectGet()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()
        // TODO - should not need to do this
        repo1Module.pom.expectHeadMissing()

        then:
        run 'retrieve'
    }

    @IgnoreIf({ GradleContextualExecuter.isParallel() })
    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "hit each remote repo only once per build and missing module"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo1Module = repo1.module("group", "projectA", "1.0")
        def repo2 = mavenHttpRepo("repo2")
        def repo2Module = repo2.module("group", "projectA", "1.0")

        settingsFile << "include 'subproject'"
        buildFile << """
            allprojects{
                repositories {
                    maven {
                        name 'repo1'
                        url '${repo1.uri}'
                    }
                    maven {
                        name 'repo2'
                        url '${repo2.uri}'
                    }
                }
            }
            configurations {
                config1
            }
            dependencies {
                config1 'group:projectA:1.0'
            }

            task resolveConfig1 {
                doLast {
                   configurations.config1.incoming.resolutionResult.allDependencies{
                        assert it instanceof UnresolvedDependencyResult
                   }
               }
            }

            project(":subproject"){
                configurations{
                    config2
                }
                dependencies{
                    config2 'group:projectA:1.0'
                }
                task resolveConfig2 {
                    doLast {
                        configurations.config2.incoming.resolutionResult.allDependencies{
                            assert it instanceof UnresolvedDependencyResult
                        }
                    }
                }
            }
        """
        when:
        repo1Module.pom.expectGetMissing()
        repo2Module.pom.expectGetMissing()

        then:
        run('resolveConfig1')

        when:
        server.resetExpectations()
        repo1Module.pom.expectGetMissing()
        repo2Module.pom.expectGetMissing()

        then:
        run "resolveConfig1", "resolveConfig2"
    }

    def "does not hit remote repositories if version is available in local repo and missing from remote repo"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo1Module = repo1.module("group", "projectA", "1.0")
        def repo2 = mavenRepo("repo2")
        def repo2Module = repo2.module("group", "projectA", "1.0")

        buildFile << """
        repositories {
           maven {
               name 'repo1'
               url '${repo1.uri}'
           }
           maven {
               name 'repo2'
               url '${repo2.uri}'
           }
       }
       configurations { compile }
       dependencies {
           compile 'group:projectA:1.0'
       }

       task retrieve(type: Sync) {
           into 'libs'
           from configurations.compile
       }
       """

        when:
        repo2Module.publish()
        repo1Module.pom.expectGetMissing()

        then:
        run 'retrieve'

        when:
        server.resetExpectations()

        then:
        run 'retrieve'
    }
}
