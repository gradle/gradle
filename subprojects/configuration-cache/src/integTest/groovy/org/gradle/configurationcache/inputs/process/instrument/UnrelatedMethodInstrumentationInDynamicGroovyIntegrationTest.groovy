/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.inputs.process.instrument

import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.test.fixtures.file.TestFile

class UnrelatedMethodInstrumentationInDynamicGroovyIntegrationTest extends AbstractProcessInstrumentationIntegrationTest implements DynamicGroovyPluginMixin {
    def "calling an unrelated method is allowed in groovy build script #indyStatus"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        generateClassesWithClashingMethods()

        withPluginCode("""
                import java.io.*
                import static ProcessGroovyMethodsExecute.execute
            """,
            """
                ProcessGroovyMethodsExecute.execute("some string")
                ProcessGroovyMethodsExecute.execute("some string", ["array"] as String[], file("test"))
                ProcessGroovyMethodsExecute.execute("some string", ["array"], file("test"))

                ProcessGroovyMethodsExecute.execute(["some", "string"] as String[])
                ProcessGroovyMethodsExecute.execute(["some", "string"] as String[], ["array"] as String[], file("test"))
                ProcessGroovyMethodsExecute.execute(["some", "string"] as String[], ["array"], file("test"))

                ProcessGroovyMethodsExecute.execute(["some", "string"])
                ProcessGroovyMethodsExecute.execute(["some", "string"], ["array"] as String[], file("test"))
                ProcessGroovyMethodsExecute.execute(["some", "string"], ["array"], file("test"))

                execute("some string")
                execute("some string", ["array"] as String[], file("test"))
                execute("some string", ["array"], file("test"))

                execute(["some", "string"] as String[])
                execute(["some", "string"] as String[], ["array"] as String[], file("test"))
                execute(["some", "string"] as String[], ["array"], file("test"))

                execute(["some", "string"])
                execute(["some", "string"], ["array"] as String[], file("test"))
                execute(["some", "string"], ["array"], file("test"))

                def e = new RuntimeExec()
                e.exec("some string")
                e.exec("some string", ["array"] as String[])
                e.exec("some string", ["array"] as String[], file("test"))
                e.exec(["some", "string"] as String[])
                e.exec(["some", "string"] as String[], ["array"] as String[])
                e.exec(["some", "string"] as String[], ["array"] as String[], file("test"))

                def s = new ProcessBuilderStart()
                s.start()
            """, enableIndy)

        when:
        configurationCacheRun("-q", ":help")

        then:
        configurationCache.assertStateStored()

        where:
        enableIndy << [true, false]
        indyStatus = enableIndy ? "with indy" : "without indy"
    }

    // Lift the visibility of the method to make it available for the mixin
    @Override
    TestFile buildScript(@GroovyBuildScriptLanguage String script) {
        super.buildScript(script)
    }
}
