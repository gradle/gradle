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

package org.gradle.play.integtest.advanced

import org.gradle.play.integtest.PlayDistributionApplicationIntegrationTest
import org.gradle.play.integtest.fixtures.AdvancedRunningPlayApp
import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp
import org.gradle.play.integtest.fixtures.PlayApp

class PlayDistributionAdvancedAppIntegrationTest extends PlayDistributionApplicationIntegrationTest {
    def setup() {
        runningApp = new AdvancedRunningPlayApp(testDirectory)
    }

    @Override
    PlayApp getPlayApp() {
        return new AdvancedPlayApp()
    }

    @Override
    void verifyArchives() {
        super.verifyArchives()

        archives()*.containsDescendants(
            "playBinary/conf/jva.routes",
            "playBinary/conf/scala.routes")
    }

    @Override
    void verifyStagedFiles() {
        super.verifyStagedFiles()

        file("build/stage/playBinary").assertContainsDescendants(
                "conf/jva.routes",
                "conf/scala.routes"
        )
    }

    @Override
    void verifyJars() {
        super.verifyJars()

        jar("build/distributionJars/playBinary/${playApp.name}.jar").containsDescendants(
                "views/html/awesome/index.class",
                "jva/html/index.class",
                "special/strangename/Application.class",
                "models/DataType.class",
                "models/ScalaClass.class",
                "controllers/scla/MixedJava.class",
                "controllers/jva/PureJava.class"
        )
    }

    @Override
    String[] getBuildTasks() {
        return super.getBuildTasks() + ":compilePlayBinaryPlayJavaTwirlTemplates"
    }
}
