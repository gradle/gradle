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

import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.PlayMultiVersionApplicationIntegrationTest
import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp

class PlayAppWithNestedResourceIntegrationTest extends PlayMultiVersionApplicationIntegrationTest {
    @Override
    PlayApp getPlayApp() {
        return new AdvancedPlayApp()
    }

    def "creates play app jar with nested resources"() {
        when:
        succeeds("createPlayBinaryJar")

        then:
        executed(
            ":compilePlayBinaryRoutes",
            ":compilePlayBinaryScala",
            ":createPlayBinaryJar")

        then:
        verifyJars()

        when:
        succeeds("createPlayBinaryJar")
        then:
        skipped(
            ":compilePlayBinaryRoutes",
            ":compilePlayBinaryScala")
    }

    void verifyJars() {
        jar("build/playBinary/lib/${playApp.name}.jar").containsDescendants(
            "evolutions/default/1.sql"
        )
    }
}
