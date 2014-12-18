/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.plugins.javascript.coffeescript

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

import static org.gradle.plugins.javascript.base.JavaScriptBasePluginTestFixtures.*
import static org.gradle.plugins.javascript.coffeescript.CoffeeScriptBasePluginTestFixtures.*

class CoffeeScriptBasePluginIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getPluginName() {
        "coffeescript-base"
    }

    def setup() {
        addApplyPluginScript(buildFile)
        addGradlePublicJsRepoScript(buildFile)
    }

    def "can download coffeescript by default"() {
        given:
        buildFile << """
            task resolve(type: Copy) {
                from javaScript.coffeeScript.js
                into "deps"
            }
        """

        when:
        run "resolve"

        then:
        def js = file("deps/coffee-script-js-1.3.3.js")
        js.exists()
        js.text.contains("CoffeeScript Compiler")
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "can compile coffeescript"() {
        given:
        file("src/main/coffeescript/dir1/thing1.coffee") << "number = 1"
        file("src/main/coffeescript/dir2/thing2.coffee") << "number = 2"

        buildFile << """
            repositories.mavenCentral()
            task compile(type: ${CoffeeScriptCompile.name}) {
                destinationDir file("build/compiled/js")
                source fileTree("src/main/coffeescript")
            }
        """

        when:
        run "compile"

        then:
        ":compile" in nonSkippedTasks

        and:
        def f1 = file("build/compiled/js/dir1/thing1.js")
        f1.exists()
        f1.text.startsWith("(function() {")

        def f2 = file("build/compiled/js/dir2/thing2.js")
        f2.exists()
        f2.text.startsWith("(function() {")

        when:
        run "compile"

        then:
        ":compile" in skippedTasks
    }
}
