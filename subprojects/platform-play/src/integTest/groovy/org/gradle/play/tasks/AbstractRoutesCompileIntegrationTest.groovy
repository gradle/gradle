/*
 * Copyright 2014 the original author or authors.
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


package org.gradle.play.tasks

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.play.integtest.fixtures.Repositories.PLAY_REPOSITORIES

abstract class AbstractRoutesCompileIntegrationTest extends MultiVersionIntegrationSpec {
    def destinationDirPath = "build/src/play/binary/routesScalaSources"
    def destinationDir = file(destinationDirPath)

    abstract getScalaRoutesFileName(String packageName, String namespace)

    abstract getJavaRoutesFileName(String packageName, String namespace)

    abstract getReverseRoutesFileName(String packageName, String namespace)

    abstract getOtherRoutesFileNames()

    def destinationDir(String sourceSetName) {
        return file("build/src/play/binary/${sourceSetName}")
    }

    def setup() {
        settingsFile << """ rootProject.name = 'routes-play-app' """
        buildFile << """
plugins {
    id 'play-application'
}

model {
    components {
        play {
            targetPlatform "play-${version}"
        }
    }
}

${PLAY_REPOSITORIES}
"""
    }

    def "can run RoutesCompile"() {
        given:
        withRoutesTemplate()
        expect:
        succeeds("compilePlayBinaryPlayRoutes")
        and:
        destinationDir.assertHasDescendants(createRouteFileList() as String[])
    }

    def "recompiles on changed routes file input"() {
        given:
        TestFile templateFile = withRoutesTemplate()
        succeeds("compilePlayBinaryPlayRoutes")

        and:
        destinationDir.assertHasDescendants(createRouteFileList() as String[])
        def scalaRoutesFileSnapshot = getScalaRoutesFile().snapshot();
        def javaRoutesFileSnapshot = getJavaRoutesFile().snapshot();
        def reverseRoutesFileSnapshot = getReverseRoutesFile().snapshot();

        when:
        // Wait to ensure timestamp on input file is different from previous compilation
        // I suspect that the Play routes compiler has some incremental check based on timestamp
        sleep(1000)
        templateFile << """
GET     /newroute                          controllers.Application.index()
"""

        and:
        succeeds "compilePlayBinaryPlayRoutes"

        then:
        executedAndNotSkipped ":compilePlayBinaryPlayRoutes"

        and:
        getScalaRoutesFile().assertContentsHaveChangedSince(scalaRoutesFileSnapshot);
        getJavaRoutesFile().assertContentsHaveChangedSince(javaRoutesFileSnapshot)
        getReverseRoutesFile().assertContentsHaveChangedSince(reverseRoutesFileSnapshot);

        when:
        succeeds "compilePlayBinaryPlayRoutes"

        then:
        skipped ":compilePlayBinaryPlayRoutes"
    }

    private TestFile getScalaRoutesFile() {
        file(destinationDirPath, getScalaRoutesFileName('', ''))
    }

    private TestFile getJavaRoutesFile() {
        file(destinationDirPath, getJavaRoutesFileName('', ''))
    }

    private TestFile getReverseRoutesFile() {
        file(destinationDirPath, getReverseRoutesFileName('', ''))
    }

    def "compiles additional routes file and cleans up output on removal"() {
        when:
        withRoutesTemplate()
        then:
        succeeds("compilePlayBinaryPlayRoutes")
        and:
        destinationDir.assertHasDescendants(createRouteFileList() as String[])

        when:
        withRoutesTemplate("foo")
        and:
        succeeds("compilePlayBinaryPlayRoutes")
        then:
        destinationDir.assertHasDescendants((createRouteFileList() + createRouteFileList('foo')) as String[])

        when:
        file("conf/foo.routes").delete()
        then:
        succeeds("compilePlayBinaryPlayRoutes")
        and:
        destinationDir.assertHasDescendants(createRouteFileList() as String[])
        createRouteFileList('foo').each { destinationDir.file(it).assertDoesNotExist() }
    }

    def "compiles multiple Routes source sets as part of play application build"() {
        withExtraSourceSets()
        withRoutesTemplate()
        withRoutesSource(file("extraRoutes", "some", "pkg", "some.pkg.routes"), ".some.pkg")
        withRoutesSource(file("otherRoutes", "other", "other.routes"), ".other")

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryPlayRoutes",
                ":compilePlayBinaryPlayExtraRoutes",
                ":compilePlayBinaryPlayOtherRoutes"
        )

        and:
        destinationDir.assertHasDescendants(createRouteFileList() as String[])
        destinationDir("extraRoutesScalaSources").assertHasDescendants(createRouteFileList('some/pkg') as String[])
        destinationDir("otherRoutesScalaSources").assertHasDescendants(createRouteFileList('other') as String[])

        and:
        jar("build/playBinary/lib/routes-play-app.jar").containsDescendants("controllers/routes.class")
        jar("build/playBinary/lib/routes-play-app.jar").containsDescendants("controllers/some/pkg/routes.class")
        jar("build/playBinary/lib/routes-play-app.jar").containsDescendants("controllers/other/routes.class")
    }

    def "extra route sources appear in the components report"() {
        withExtraSourceSets()

        when:
        succeeds "components"

        then:
        output.contains """
Play Application 'play'
-----------------------

Source sets
    Java source 'play:java'
        srcDir: app
        includes: **/*.java
    JVM resources 'play:resources'
        srcDir: conf
    Routes source 'play:extraRoutes'
        srcDir: extraRoutes
    Routes source 'play:otherRoutes'
        srcDir: otherRoutes
    Routes source 'play:routes'
        srcDir: conf
        includes: routes, *.routes
    Scala source 'play:scala'
        srcDir: app
        includes: **/*.scala
    Twirl template source 'play:twirlTemplates'
        srcDir: app
        includes: **/*.scala.*

Binaries
"""
    }

    def "can run RoutesCompile with namespaceReverseRouter set"() {
        given:
        withRoutesTemplate("org.gradle.test")
        buildFile << """
            model {
                components {
                    play {
                        tasks.withType(RoutesCompile) {
                            namespaceReverseRouter = true
                        }
                    }
                }
            }
        """
        expect:
        succeeds("compilePlayBinaryPlayRoutes")
        and:
        destinationDir.assertHasDescendants(createRouteFileList("org/gradle/test", "org/gradle/test") as String[])
    }

    def withRoutesSource(TestFile routesFile, String packageId) {
        routesFile.createFile()
        routesFile << """
# Routes
# This file defines all application routes (Higher priority routes first)
# ~~~~

# Home page
GET     /                          controllers${packageId}.Application.index()
"""
        withControllerSource(file("app/controllers/${packageId}/Application.scala"), packageId)
        return routesFile
    }

    def withControllerSource(TestFile file, String packageId) {
        file.createFile()
        file << """
package controllers${packageId}

import javax.inject._
import play.api._
import play.api.mvc._
import models._

@Singleton
class Application @Inject() extends InjectedController {
  def index = Action {
    Ok("Your new application is ready.")
  }
}
"""
    }

    def withRoutesTemplate(String packageName = "") {
        def routesFile = packageName.isEmpty() ? file("conf", "routes") : file("conf", packageName + ".routes")
        def packageId = packageName.isEmpty() ? "" : ".$packageName"
        withRoutesSource(routesFile, packageId)
    }

    def createRouteFileList(String packageName = '', String namespace='') {
        [getJavaRoutesFileName(packageName, namespace), getReverseRoutesFileName(packageName, namespace), getScalaRoutesFileName(packageName, namespace)] + otherRoutesFileNames.collect { it(packageName, namespace) }
    }

    def withExtraSourceSets() {
        buildFile << """
            model {
                components {
                    play {
                        sources {
                            extraRoutes(RoutesSourceSet) {
                                source.srcDir "extraRoutes"
                            }
                            otherRoutes(RoutesSourceSet) {
                                source.srcDir "otherRoutes"
                            }
                        }
                    }
                }
            }
        """
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }

    def "can add additional imports"() {
        given:
        withRoutesTemplate()
        and:
        buildFile << """
model {
    components {
        play {
            tasks.withType(RoutesCompile) {
                additionalImports << "extra.package"
            }
        }
    }
}
"""
        expect:
        succeeds("compilePlayBinaryPlayRoutes")
        and:
        destinationDir.file(getReverseRoutesFileName('', '')).text.contains("extra.package")
        destinationDir.file(getScalaRoutesFileName('', '')).text.contains("extra.package")
    }
}
