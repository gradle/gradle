/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api

import org.gradle.api.plugins.ExtensionAware
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.Flaky
import spock.lang.Issue

@Flaky(because = "https://github.com/gradle/gradle-private/issues/3534")
class ConcurrentClassDecorationSpec extends AbstractIntegrationSpec {

    @Issue("https://issues.gradle.org/browse/GRADLE-2836")
    def "can decorate classes concurrently"() {
        given:
        file("buildSrc/src/main/java/Thing.java") << "public class Thing {}"
        ("a".."d").each { name ->
            createDirs(name)
            settingsFile << "include '$name'\n"
            file("$name/build.gradle") << """
                task decorateClass {
                    doLast {
                        def instantiator = services.get(${Instantiator.name})
                        def thing = instantiator.newInstance(Thing)
                        assert thing instanceof ${ExtensionAware.name}
                    }
                }
            """
        }

        when:
        args "--parallel"

        then:
        succeeds "decorateClass"
    }

}
