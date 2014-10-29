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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class RoutesCompileIntegrationTest extends AbstractIntegrationSpec {
    def setup(){
        buildFile << """
        repositories{
            ivy {
                url "http://repo.typesafe.com/typesafe/releases/"
                layout "maven"
            }
        }

        configurations{
            playRoutes
        }

        dependencies{
            playRoutes "com.typesafe.play:routes-compiler_2.10:2.3.5"
            playRoutes "org.scala-lang:scala-library:2.10.4"
            playRoutes "commons-io:commons-io:2.0.1"
        }

        task routesCompile(type:RoutesCompile) {
            compilerClasspath = configurations.playRoutes
            outputDirectory = file('build/routes')
        }
"""
    }

    def "can run RoutesCompile"(){
        given:
        withRoutesTemplate()
        expect:
        succeeds("routesCompile")
        and:
        file("build/routes").assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
    }

    def "runs compiler incrementally"(){
        when:
        withRoutesTemplate()
        then:
        succeeds("routesCompile")
        and:
        file("build/routes").assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
        def routesFirstCompileSnapshot = file("build/routes/controllers/routes.java").snapshot();
        def revRoutingFirstCompileSnapshot = file("build/routes/routes_reverseRouting.scala").snapshot();
        def routingFirstCompileSnapshot = file("build/routes/routes_routing.scala").snapshot();

        withRoutesTemplate("foo")
        and:
        succeeds("routesCompile")
        then:
        file("build/routes").assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
        and:
        file("build/routes/controllers/routes.java").assertHasNotChangedSince(routesFirstCompileSnapshot)
        file("build/routes/routes_reverseRouting.scala").assertHasNotChangedSince(revRoutingFirstCompileSnapshot)
        file("build/routes/routes_routing.scala").assertHasNotChangedSince(routingFirstCompileSnapshot)

        when:
        file("conf/foo.routes").delete()
        then:
        succeeds("routesCompile")
        and:
        file("build/routes/foo").assertHasDescendants()
    }

    def "removes stale output files in incremental compile"(){
        given:
        TestFile file = withRoutesTemplate()
        succeeds("routesCompile")

        and:
        file("build/routes").assertHasDescendants("controllers/routes.java", "routes_reverseRouting.scala", "routes_routing.scala")
        def routesFirstCompileSnapshot = file("build/routes/controllers/routes.java").snapshot();
        def revRoutingFirstCompileSnapshot = file("build/routes/routes_reverseRouting.scala").snapshot();
        def routingFirstCompileSnapshot = file("build/routes/routes_routing.scala").snapshot();

        when:
        file.delete()

        then:
        succeeds("routesCompile")
        and:
        file("build/routes/controllers/routes.java").assertHasNotChangedSince(routesFirstCompileSnapshot);
        file("build/routes/routes_reverseRouting.scala").assertHasNotChangedSince(revRoutingFirstCompileSnapshot);
        file("build/routes/routes_routing.scala").assertHasNotChangedSince(routingFirstCompileSnapshot);
    }

    def withRoutesTemplate(String packageName = "") {
        def routesFile = packageName.isEmpty() ? file("conf", "routes") : file("conf", packageName + ".routes")

        routesFile.createFile()
        routesFile << """
# Routes
# This file defines all application routes (Higher priority routes first)
# ~~~~

# Home page
GET     /                          controllers.Application.index()

# Map static resources from the /public folder to the /assets URL path
GET     /assets/*file               controllers.Assets.at(path="/public", file)
"""
        buildFile << """
routesCompile.source '${routesFile.toURI()}'
"""

        return routesFile
    }
}
