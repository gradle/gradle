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

import org.apache.commons.lang.StringUtils
import org.gradle.play.integtest.fixtures.PlayMultiVersionIntegrationTest
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

class RoutesCompileIntegrationTest extends PlayMultiVersionIntegrationTest {
    def destinationDirPath = "build/playBinary/src/routesCompileRoutesSourcesPlayBinary"
    def destinationDir = file(destinationDirPath)

    def setup() {
        settingsFile << """ rootProject.name = 'routes-play-app' """
        buildFile <<"""
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

repositories{
    jcenter()
    maven{
        name = "typesafe-maven-release"
        url = "https://repo.typesafe.com/typesafe/maven-releases"
    }
}
"""
    }

    def "can run RoutesCompile"() {
        given:
        withRoutesTemplate()
        expect:
        succeeds("routesCompileRoutesSourcesPlayBinary")
        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
    }

    def "runs compiler multiple times"(){
        when:
        withRoutesTemplate()
        then:
        succeeds("routesCompileRoutesSourcesPlayBinary")
        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")

        withRoutesTemplate("foo")
        and:
        succeeds("routesCompileRoutesSourcesPlayBinary")
        then:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala",
                "controllers/foo/routes.java", "foo/routes_reverseRouting.scala", "foo/routes_routing.scala")

        when:
        file("conf/foo.routes").delete()
        then:
        succeeds("routesCompileRoutesSourcesPlayBinary")
        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
    }

    def "removes stale output files in incremental compile"(){
        given:
        TestFile templateFile = withRoutesTemplate()
        succeeds("routesCompileRoutesSourcesPlayBinary")

        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
        def routesFirstCompileSnapshot = file(destinationDirPath, "controllers/routes.java").snapshot();
        def revRoutingFirstCompileSnapshot = file(destinationDirPath, "routes_reverseRouting.scala").snapshot();
        def routingFirstCompileSnapshot = file(destinationDirPath, "routes_routing.scala").snapshot();

        when:
        templateFile.delete()

        then:
        succeeds("routesCompileRoutesSourcesPlayBinary")
        and:
        file(destinationDirPath, "controllers/routes.java").assertHasNotChangedSince(routesFirstCompileSnapshot);
        file(destinationDirPath, "routes_reverseRouting.scala").assertHasNotChangedSince(revRoutingFirstCompileSnapshot);
        file(destinationDirPath, "routes_routing.scala").assertHasNotChangedSince(routingFirstCompileSnapshot);
    }

    def "compiles multiple Routes source sets as part of play application build" () {
        withExtraSourceSets()
        withRoutesTemplate()
        withRoutesSource(file("extraRoutes", "some", "pkg", "some.pkg.routes"), ".some.pkg")
        withRoutesSource(file("otherRoutes", "other", "other.routes"), ".other")

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":routesCompileRoutesSourcesPlayBinary",
                ":routesCompileExtraRoutesPlayBinary",
                ":routesCompileOtherRoutesPlayBinary"
        )

        and:
        destinationDir.assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
        destinationDir("extraRoutes").assertHasDescendants("controllers/some/pkg/routes.java", "some/pkg/routes_reverseRouting.scala", "some/pkg/routes_routing.scala")
        destinationDir("otherRoutes").assertHasDescendants("controllers/other/routes.java", "other/routes_reverseRouting.scala", "other/routes_routing.scala")

        and:
        jar("build/playBinary/lib/routes-play-app.jar").containsDescendants("controllers/routes.class")
        jar("build/playBinary/lib/routes-play-app.jar").containsDescendants("controllers/some/pkg/routes.class")
        jar("build/playBinary/lib/routes-play-app.jar").containsDescendants("controllers/other/routes.class")
    }

    def "extra route sources appear in the components report" () {
        withExtraSourceSets()

        when:
        succeeds "components"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
Play Application 'play'
-----------------------

Source sets
    Routes source 'play:extraRoutes'
        extraRoutes
    Java source 'play:java'
        app
        includes: **/*.java
    Routes source 'play:otherRoutes'
        otherRoutes
    JVM resources 'play:resources'
        conf
    Routes source 'play:routesSources'
        conf
        includes: routes, *.routes
    Scala source 'play:scala'
        app
        includes: **/*.scala
    Twirl template source 'play:twirlTemplates'
        app
        includes: **/*.html

Binaries
"""))
    }

    def destinationDir(String sourceSetName) {
        return file("build/playBinary/src/routesCompile${StringUtils.capitalize(sourceSetName)}PlayBinary")
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


import play.api._
import play.api.mvc._
import models._

object Application extends Controller {
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
}
