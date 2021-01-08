/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugins.ide

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

abstract class AbstractIdeDeduplicationIntegrationTest extends AbstractIdeProjectIntegrationTest {

    @ToBeFixedForConfigurationCache
    def "unique project names are not deduplicated"() {
        given:
        project("root") {
            project("foo") {
                project("bar") {}
            }
            project("foobar") {
                project("app") {}
            }
        }

        when:
        run ideName

        then:
        projectName(".") == "root"
        projectName("foo") == "foo"
        projectName("foo/bar") == "bar"
        projectName("foobar") == "foobar"
        projectName("foobar/app") == "app"
    }

    @ToBeFixedForConfigurationCache
    def "deduplicates duplicate ide project names"() {
        given:
        project("root") {
            project("foo") {
                project("app") {}
            }
            project("bar") {
                project("app") {}
            }
        }

        when:
        run ideName

        then:
        projectName(".") == "root"
        projectName("foo") == "foo"
        projectName("foo/app") == "foo-app"
        projectName("bar") == "bar"
        projectName("bar/app") == "bar-app"
    }

    @ToBeFixedForConfigurationCache
    def "dedups child project with same name as parent project"() {
        given:
        project("root") {
            project("app") {
                project("app") {}
            }
        }

        when:
        run ideName

        then:
        projectName(".") == "root"
        projectName("app") == "root-app"
        projectName("app/app") == "app-app"

    }

    @ToBeFixedForConfigurationCache
    def "handles calculated name matches existing project name"() {
        given:
        project("root") {
            project("root-foo-bar") {}
            project("foo-bar") {}
            project("foo") {
                project("bar") {}
            }
            project("baz") {
                project("bar") {}
            }
        }

        when:
        run ideName

        then:
        projectName(".") == "root"
        projectName("root-foo-bar") == "root-root-foo-bar"
        projectName("foo-bar") == "foo-bar"
        projectName("foo") == "foo"
        projectName("foo/bar") == "root-foo-bar"
        projectName("baz/bar") == "baz-bar"
    }

    @ToBeFixedForConfigurationCache
    def "dedups projects with different nested level"() {
        given:
        project("root") {
            project("app") {}
            project("services") {
                project("bar") {
                    project("app") {}
                }
            }
        }

        when:
        run ideName

        then:
        projectName(".") == "root"
        projectName("app") == "root-app"
        projectName("services") == "services"
        projectName("services/bar") == "bar"
        projectName("services/bar/app") == "bar-app"
    }

    @ToBeFixedForConfigurationCache
    def "dedups root project name"() {
        given:
        project("app") {
            project("app") {}
        }

        when:
        run ideName

        then:
        projectName(".") == "app"
        projectName("app") == "app-app"
    }

    @ToBeFixedForConfigurationCache
    def "deduplication works on deduplicated parent module name"() {
        given:
        project("root") {
            project("bar") {
                project("services") {
                    project("rest") {}
                }
            }
            project("foo") {
                project("services") {
                    project("rest") {}
                }
            }
        }

        when:
        run ideName

        then:
        projectName(".") == "root"
        projectName("bar/services") == "bar-services"
        projectName("bar/services/rest") == "bar-services-rest"
        projectName("foo/services") == "foo-services"
        projectName("foo/services/rest") == "foo-services-rest"
    }

    @ToBeFixedForConfigurationCache
    def "allows deduplication with parent not part of the target list"() {
        given:
        project("root") {
            project("bar") {
                project("services") {
                    project("rest") {}
                }
            }
            project("foo") {
                project("services") {
                    project("rest") {}
                }
            }
        }

        when:
        run ideName

        then:
        projectName(".") == "root"
        projectName("bar/services") == "bar-services"
        projectName("bar/services/rest") == "bar-services-rest"
        projectName("foo/services") == "foo-services"
        projectName("foo/services/rest") == "foo-services-rest"
    }

    @ToBeFixedForConfigurationCache
    def "allows deduplication when root project does not apply IDE plugin"() {
        given:
        project("root", false) {
            project("foo") {
                project("app") {}
            }
            project("bar") {
                project("app") {}
            }
        }

        when:
        run ideName

        then:
        projectName("foo") == "foo"
        projectName("foo/app") == "foo-app"
        projectName("bar") == "bar"
        projectName("bar/app") == "bar-app"
    }

    @ToBeFixedForConfigurationCache
    def "removes duplicate words from project dedup prefix"() {
        given:
        project("root"){
            project("api"){
                project("myproject") {
                    project("myproject-foo") {
                        project("app") {}
                    }
                }

            }
            project("impl"){
                project("myproject") {
                    project("myproject-foo") {
                        project("app") {}
                    }
                }
            }
        }

        when:
        run ideName

        then:
        projectName(".") == "root"
        projectName("api") == "api"
        projectName("api/myproject") == "api-myproject"
        projectName("api/myproject/myproject-foo") == "api-myproject-foo"
        projectName("api/myproject/myproject-foo/app") == "api-myproject-foo-app"

        projectName("impl") == "impl"
        projectName("impl/myproject") == "impl-myproject"
        projectName("impl/myproject/myproject-foo") == "impl-myproject-foo"
        projectName("impl/myproject/myproject-foo/app") == "impl-myproject-foo-app"
    }

    @ToBeFixedForConfigurationCache
    def "will use configured module name"() {
        given:
        project("root") {
            project("foo") {
                project("app") {
                    buildFile << "${configuredModule}.name = 'custom-app'"
                }
            }
            project("bar") {
                project("app") {}
            }
        }

        when:
        run ideName

        then:
        projectName("foo") == "foo"
        projectName("foo/app") == "custom-app"
        projectName("bar") == "bar"
        projectName("bar/app") == "bar-app"
    }

    @ToBeFixedForConfigurationCache
    def "will not de-duplicate module that conflicts with configured module name"() {
        given:
        project("root") {
            project("foo") {
                project("other") {
                    buildFile << "${configuredModule}.name = 'app'"
                }
            }
            project("bar") {
                project("app") {}
            }
        }

        when:
        run ideName

        then:
        projectName("foo") == "foo"
        projectName("foo/other") == "app"
        projectName("bar") == "bar"
        projectName("bar/app") == "app"
    }

}
