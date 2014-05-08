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

package org.gradle.plugin

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ScriptPluginClassLoadingIntegrationTest extends AbstractIntegrationSpec {

    @Issue("http://issues.gradle.org/browse/GRADLE-3069")
    def "second level and beyond script plugins have same class loader scope as original target"() {
        when:
        file("buildSrc/src/main/java/pkg/Thing.java") << """
            package pkg;
            public class Thing {
              public String getMessage() { return "hello"; }
            }
        """

        file("plugin1.gradle") << """
            task sayMessageFrom1 << { println new pkg.Thing().getMessage() }
            apply from: 'plugin2.gradle'
        """

        file("plugin2.gradle") << """
            task sayMessageFrom2 << { println new pkg.Thing().getMessage() }
            apply from: 'plugin3.gradle'
        """

        file("plugin3.gradle") << """
            task sayMessageFrom3 << { println new pkg.Thing().getMessage() }
        """

        buildScript "apply from: 'plugin1.gradle'"

        then:
        succeeds "sayMessageFrom1", "sayMessageFrom2", "sayMessageFrom3"
        output.contains "hello"
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-3079")
    def "methods defined in script are available to used script plugins"() {
        given:
        buildScript """
          def addTask(project) {
            project.tasks.create("hello").doLast { println "hello from method" }
          }

          apply from: "script.gradle"
        """

        file("script.gradle") << "addTask(project)"

        when:
        succeeds "hello"

        then:
        output.contains "hello from method"
    }
}
