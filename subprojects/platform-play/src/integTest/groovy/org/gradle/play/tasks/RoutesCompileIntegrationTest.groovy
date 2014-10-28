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
        }

        task routesCompile(type:RoutesCompile) {
            compilerClasspath = configurations.playRoutes
            outputDirectory = file('build/routes')
        }
"""
    }

    /**
     * TODO elaborate
     * */
    def "can run RoutesCompile"(){
        given:
        withRoutesTemplate()
        expect:
        succeeds("routesCompile")
    }

    def withRoutesTemplate() {
        def routesFile = file("conf", "routes")
        routesFile.createFile()
        routesFile << """
# Routes
# This file defines all application routes (Higher priority routes first)
# ~~~~

# Home page
GET     /                           controllers.Application.index()

# Map static resources from the /public folder to the /assets URL path
GET     /assets/*file               controllers.Assets.at(path="/public", file)
"""
        buildFile << "routesCompile.source '${routesFile.toURI()}'"


    }
}
