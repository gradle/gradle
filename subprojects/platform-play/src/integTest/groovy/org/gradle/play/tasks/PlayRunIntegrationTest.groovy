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

package org.gradle.play.tasks

import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.PlayMultiVersionRunApplicationIntegrationTest
import org.gradle.play.integtest.fixtures.app.BasicPlayApp

class PlayRunIntegrationTest extends PlayMultiVersionRunApplicationIntegrationTest {
    PlayApp playApp = new BasicPlayApp()

    def setup() {
        buildFile << """
            model {
                tasks.runPlayBinary {
                    httpPort = 0
                }
            }
        """
    }

    def "play run container classloader is isolated from the worker process classloader"() {
        withLoadProjectClassController()

        setup:
        // build once to speed up the playRun build and avoid spurious timeouts
        succeeds "assemble"

        when:
        startBuild "runPlayBinary"

        then:
        runningApp.verifyStarted()
        runningApp.playUrl("loadProjectClass").text.contains("SUCCESS: class not found")

        cleanup:
        build.cancelWithEOT().waitForFinish()
        runningApp.verifyStopped()
    }

    void withLoadProjectClassController() {
        file("app/controllers/jva/LoadProjectClass.java").text = loadProjectClassController
        file("conf/routes") << """
GET        /loadProjectClass         controllers.jva.LoadProjectClass.index
"""
    }

    String getLoadProjectClassController() {
        return """
package controllers.jva;

import play.*;
import play.mvc.*;

public class LoadProjectClass extends Controller {

    public static Result index() {
        try {
            LoadProjectClass.class.getClassLoader().loadClass("org.gradle.api.Project");
        } catch (ClassNotFoundException e) {
            return ok("SUCCESS: class not found");
        }
        return ok("FAILED: class was found");
    }

}
"""
    }
}
