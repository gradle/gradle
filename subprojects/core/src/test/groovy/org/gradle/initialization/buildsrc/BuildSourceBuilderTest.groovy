/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.StartParameter
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.initialization.GradleLauncher
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.progress.BuildOperationDetails
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.internal.Factory
import org.junit.Rule
import spock.lang.Specification

class BuildSourceBuilderTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def launcherFactory = Mock(GradleLauncherFactory)
    def classLoaderScope = Mock(ClassLoaderScope)
    def cacheRepository = Mock(CacheRepository)
    def executor = Mock(BuildOperationExecutor)
    def buildSourceBuilder = Spy(BuildSourceBuilder, constructorArgs: [launcherFactory, classLoaderScope,  cacheRepository, executor])

    def parameter = new StartParameter()

    void "creates classpath when build src does not exist"() {
        when:
        parameter.setCurrentDir(new File('nonexisting'))

        then:
        buildSourceBuilder.createBuildSourceClasspath(parameter).asFiles == []
    }

    void "creates classpath when build src exists"() {
        def cache = Mock(PersistentCache)
        def classpath = Mock(ClassPath)
        def launcher = Mock(GradleLauncher)
        executor.run(_, _) >> { BuildOperationDetails details, Factory factory -> return factory.create() }
        launcherFactory.newInstance(_) >> launcher
        buildSourceBuilder.createCache(parameter) >> cache
        cache.useCache(_ as String, _ as BuildSrcUpdateFactory) >> classpath

        when:
        parameter.setCurrentDir(tmpDir.createDir("someDir"));

        then:
        buildSourceBuilder.createBuildSourceClasspath(parameter) == classpath
    }
}
