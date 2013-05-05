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

package org.gradle.initialization.buildsrc

import spock.lang.Specification
import org.gradle.cache.PersistentCache
import org.gradle.GradleLauncher
import org.junit.Rule
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider

/**
 * by Szczepan Faber, created at: 3/18/13
 */
class BuildSrcUpdateFactoryTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def cache = Stub(PersistentCache)
    def launcher = Stub(GradleLauncher)
    def listener = Stub(BuildSrcBuildListenerFactory.Listener)
    def listenerFactory = Mock(BuildSrcBuildListenerFactory)
    def factory = new BuildSrcUpdateFactory(cache, launcher, listenerFactory)

    def "creates classpath"() {
        cache.getBaseDir() >> temp.testDirectory
        listener.getRuntimeClasspath() >> [new File("dummy")]

        when:
        def classpath = factory.create()

        then:
        classpath.asFiles == [new File("dummy")]
        1 * listenerFactory.create(_) >> listener
    }

    def "uses listener with rebuild off when marker file present"() {
        temp.createFile("built.bin")
        cache.getBaseDir() >> temp.testDirectory

        when:
        factory.create()

        then:
        1 * listenerFactory.create(false) >> listener
    }

    def "uses listener with rebuild on when marker file not present"() {
        cache.getBaseDir() >> temp.createDir("empty")

        when:
        factory.create()

        then:
        1 * listenerFactory.create(true) >> listener
    }
}
