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

import org.gradle.play.integtest.fixtures.CancellableGradleBuild
import org.gradle.play.integtest.fixtures.PlayMultiVersionRunApplicationIntegrationTest
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp


class PlayRunIntegrationTest extends PlayMultiVersionRunApplicationIntegrationTest {
    PlayApp playApp = new BasicPlayApp()
    def build = new CancellableGradleBuild(executer)

    def setup() {
        buildFile << """
            model {
                tasks.runPlayBinary {
                    httpPort = ${runningApp.selectPort()}
                }
            }
        """
    }

    def "play run container classloader is isolated from the worker process classloader"() {
        buildFile << """
            dependencies {
                play 'com.google.guava:guava:13.0'
            }
        """
        withGuavaJarController()

        when:
        build.start("runPlayBinary")

        then:
        runningApp.verifyStarted()
        // The following should get the configured version of guava and not the version in the worker process classloader
        runningApp.playUrl("guavaJar").text.contains("guava-13.0.jar")

        cleanup:
        build.cancel()
        runningApp.verifyStopped()
    }

    void withGuavaJarController() {
        file("app/controllers/jva").mkdirs()
        file("app/controllers/jva/GuavaJar.java").text = guavaJarController
        file("conf/routes") << """
GET        /guavaJar         controllers.jva.GuavaJar.index
"""
    }

    String getGuavaJarController() {
        return """
package controllers.jva;

import play.*;
import play.mvc.*;
import views.html.*;

public class GuavaJar extends Controller {

    public static Result index() {
        String guavaJar = com.google.common.util.concurrent.MoreExecutors.class.getResource("MoreExecutors.class").toString();
        return ok(guavaJar);
    }

}
"""
    }
}
