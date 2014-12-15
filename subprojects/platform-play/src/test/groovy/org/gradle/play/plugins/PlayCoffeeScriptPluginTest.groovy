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

package org.gradle.play.plugins

import org.gradle.language.coffeescript.CoffeeScriptSourceSet
import org.gradle.play.PlayApplicationBinarySpec
import org.gradle.play.PlayApplicationSpec
import org.gradle.util.TestUtil
import spock.lang.Specification

class PlayCoffeeScriptPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(PlayApplicationPlugin);
        project.pluginManager.apply(PlayCoffeeScriptPlugin)
    }

    def "adds coffeescript source sets to play components" () {
        when:
        project.model { components { play(PlayApplicationSpec) } }
        project.evaluate()

        then:
        project.binaries.withType(PlayApplicationBinarySpec).each { PlayApplicationBinarySpec spec ->
            assert spec.getSource().find { it.name == "coffeeScriptAssets" && it instanceof CoffeeScriptSourceSet } != null
        }
    }
}
