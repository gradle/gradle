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

package org.gradle.plugins.ide.eclipse

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.ConfigureUtil

class EclipseProjectNameDeduplicationIntegrationTest extends AbstractEclipseIntegrationSpec {


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
        run "eclipse"

        then:
        project.projectName == "root"
        project("foo").projectName == "foo"
        project("foo/bar").projectName == "bar"
        project("foobar").projectName == "foobar"
        project("foobar/app").projectName == "app"
    }

    def "deduplicates duplicate eclipse project names"() {
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
        run "eclipse"

        then:
        project.projectName == "root"
        project("foo").projectName == "foo"
        project("foo/app").projectName == "foo-app"
        project("bar").projectName == "bar"
        project("bar/app").projectName == "bar-app"
    }

    def "dedups child project with same name as root project"() {
        given:
        project("app") {
            project("app") {}
        }

        when:
        run "eclipse"

        then:
        project.projectName == "app"
        project("app").projectName == "app-app"

    }

    def "handles calculated name matches existing project name"() {
        given:
        project("root") {
            project("foo-bar") {}
            project("foo") {
                project("bar") {}
            }
            project("baz") {
                project("bar") {}
            }
        }

        when:
        run "eclipse"

        then:
        project.projectName == "root"
        project("foo-bar").projectName == "foo-bar"
        project("foo").projectName == "foo"
        project("foo/bar").projectName == "root-foo-bar"
        project("baz/bar").projectName == "root-baz-bar"
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
        run "eclipse"

        then:
        project.projectName == "root"
        project("app").projectName == "root-app"
        project("services").projectName == "services"
        project("services/bar").projectName == "bar"
        project("services/bar/app").projectName == "bar-app"
    }

    def "explicit configured eclipse project name is used"() {
        given:
        project("root") {
            project("foo") {
                project("app") {
                    buildFile << "eclipse.project.name = 'custom-app'"
                }
            }
            project("bar") {
                project("app") {}
            }
        }

        when:
        run "eclipse"

        then:
        project.projectName == "root"
        project("foo").projectName == "foo"
        project("foo/app").projectName == "custom-app"
        project("bar").projectName == "bar"
        project("bar/app").projectName == "app"
    }

    def "duplicate words in project names are removed"() {
        given:
        project("myproject") {
            project("myproject-app") {
            }
            project("myproject-bar") {
                project("myproject-app") {

                }
            }
        }

        when:
        run "eclipse"

        then:
        project.projectName == "myproject"
        project("myproject-app").projectName == "myproject-app"
        project("myproject-bar").projectName == "myproject-bar"
        project("myproject-bar/myproject-app").projectName == "myproject-bar-app"
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
        run "eclipse"

        then:
        project("bar/services").projectName == "bar-services"
        project("bar/services/rest").projectName == "bar-services-rest"
        project("foo/services").projectName == "foo-services"
        project("foo/services/rest").projectName == "foo-services-rest"
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
        run "eclipse"

        then:
        project("bar/services").projectName == "bar-services"
        project("bar/services/rest").projectName == "bar-services-rest"
        project("foo/services").projectName == "foo-services"
        project("foo/services/rest").projectName == "foo-services-rest"
    }

    Project project(String projectName, Closure configClosure) {
        buildFile.createFile().text = """
allprojects {
    apply plugin:'java'
    apply plugin:'eclipse'
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

    class Project {
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
