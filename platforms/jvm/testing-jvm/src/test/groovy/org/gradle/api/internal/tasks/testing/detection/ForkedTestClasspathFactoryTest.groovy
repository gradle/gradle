/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection

import org.gradle.api.internal.classpath.Module
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.classpath.ClassPath
import spock.lang.Specification

/**
 * Tests {@link ForkedTestClasspathFactory}.
 */
class ForkedTestClasspathFactoryTest extends Specification {

    // The number of internal and external implementation jars loaded from the distribution regardless of framework.
    private static final int NUM_INTERNAL_JARS = 30
    private static final int NUM_EXTERNAL_JARS = 6

    ModuleRegistry moduleRegistry = Mock(ModuleRegistry) {
        getModule(_) >> { module(it[0], false) }
        getExternalModule(_) >> { module(it[0], true) }
    }

    ForkedTestClasspathFactory underTest = new ForkedTestClasspathFactory(moduleRegistry)

    def "creates a limited implementation classpath"() {
        when:
        def classpath = underTest.create([new File("cls.jar")], [new File("mod.jar")])

        then:
        classpath.applicationClasspath == [new File("cls.jar")]
        classpath.applicationModulepath == [new File("mod.jar")]
        classpath.implementationClasspath.size() == NUM_INTERNAL_JARS + NUM_EXTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS
    }

    def "input classpath classes are added to the application classpath"() {
        when:
        def classpath = underTest.create([
            new File("app-cls-1.0.jar"), new File("app-mod-1.0.jar"), new File("impl-cls-1.0.jar"), new File("impl-mod-1.0.jar")
        ], [])

        then:
        classpath.applicationClasspath == [new File("app-cls-1.0.jar"), new File("app-mod-1.0.jar"), new File("impl-cls-1.0.jar"), new File("impl-mod-1.0.jar")]
        classpath.applicationModulepath.isEmpty()
        classpath.implementationClasspath.size() == NUM_INTERNAL_JARS + NUM_EXTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS
    }

    def "input modulepath classes are added to the application modulepath"() {
        when:
        def classpath = underTest.create([], [
            new File("app-cls-1.0.jar"), new File("app-mod-1.0.jar"), new File("impl-cls-1.0.jar"), new File("impl-mod-1.0.jar")
        ])

        then:
        classpath.applicationClasspath.isEmpty()
        classpath.applicationModulepath == [new File("app-cls-1.0.jar"), new File("app-mod-1.0.jar"), new File("impl-cls-1.0.jar"), new File("impl-mod-1.0.jar")]
        classpath.implementationClasspath.size() == NUM_INTERNAL_JARS + NUM_EXTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS
    }

    def module(String module, boolean external) {
        String extra = external ? "external" : "internal"
        return Mock(Module) {
            getImplementationClasspath() >> {
                Mock(ClassPath) {
                    getAsURLs() >> { [new URL("file://${module}-${extra}.jar")] }
                    getAsFiles() >> { [new File("${module}-${extra}.jar")] }
                }
            }
        }
    }

}
