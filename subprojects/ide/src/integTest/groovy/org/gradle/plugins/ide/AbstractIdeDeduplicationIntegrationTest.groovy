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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.ConfigureUtil

abstract class AbstractIdeDeduplicationIntegrationTest extends AbstractIdeIntegrationSpec {
    protected abstract String projectName(String path)

    protected abstract String getIdeName()

    protected abstract String getConfiguredModule()

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
        projectName("bar/app") == "app"
    }

    def "will de-duplicate module that conflicts with configured module name"() {
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
        projectName("foo/other") == "foo-app"
        projectName("bar") == "bar"
        projectName("bar/app") == "bar-app"
    }

    Project project(String projectName, boolean allProjects = true, Closure configClosure) {
        String applyTo = allProjects ? "allprojects" : "subprojects"
        buildFile.createFile().text = """
$applyTo {
    apply plugin:'java'
    apply plugin:'$ideName'
}
"""
        settingsFile.createFile().text = "rootProject.name='$projectName'\n"
        def proj = new Project(name: projectName, path: "", projectDir: getTestDirectory())
        ConfigureUtil.configure(configClosure, proj);

        def includeSubProject
        includeSubProject = { Project p ->
            for (Project subProj : p.subProjects) {
                settingsFile << "include '${subProj.path}'\n"
                includeSubProject.trampoline().call(subProj)
            }
        }

        includeSubProject.trampoline().call(proj);
    }

    public static class Project {
        String name
        String path
        def subProjects = []
        TestFile projectDir

        def project(String projectName, Closure configClosure) {
            def p = new Project(name: projectName, path: "$path:$projectName", projectDir: projectDir.createDir(projectName));
            subProjects << p;
            ConfigureUtil.configure(configClosure, p);
        }

        File getBuildFile() {
            def buildFile = projectDir.file("build.gradle")
            if (!buildFile.exists()) {
                buildFile.createFile()
            }
            return buildFile
        }
    }

}
