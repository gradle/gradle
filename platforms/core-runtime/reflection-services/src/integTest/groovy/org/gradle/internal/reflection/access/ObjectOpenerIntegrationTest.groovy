/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.reflection.access

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.jpms.ModuleJarFixture
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

@Issue("https://github.com/gradle/gradle/issues/20089")
class ObjectOpenerIntegrationTest extends AbstractIntegrationSpec {

    @LeaksFileHandles("Module JAR is held by the module classloader")
    def "rejects making an AccessibleObject accessible for a class loaded into a custom non-boot module layer"() {
        def jarFile = file('libs/custom.jar')
        jarFile.parentFile.mkdirs()
        jarFile.bytes = ModuleJarFixture.moduleJar('custom')

        buildFile """
            import java.lang.module.ModuleFinder
            import org.gradle.api.internal.project.ProjectInternal
            import org.gradle.internal.reflection.access.ObjectOpener

            def jarPath = file('libs/custom.jar').toPath()
            def finder = ModuleFinder.of(jarPath)
            def parent = ModuleLayer.boot()
            def config = parent.configuration().resolve(finder, ModuleFinder.of(), ['custom'])
            def layer = parent.defineModulesWithOneLoader(config, getClass().classLoader)
            def moduleCls = layer.findLoader('custom').loadClass('custom.CustomClass')
            def ctor = moduleCls.getDeclaredConstructor()
            def opener = ((ProjectInternal) project).services.get(ObjectOpener)

            task ok {
                doFirst {
                    opener.makeAccessible(ctor)
                }
            }
        """

        when:
        fails "ok"

        then:
        failure.assertHasCause("Cannot open package 'custom' in module")
        failure.assertThatCause(containsString("Only boot layer modules are supported"))
    }
}
