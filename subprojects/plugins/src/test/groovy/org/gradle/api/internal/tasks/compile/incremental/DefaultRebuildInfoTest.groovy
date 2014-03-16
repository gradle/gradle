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

package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo
import org.gradle.api.tasks.util.PatternSet
import spock.lang.Specification

class DefaultRebuildInfoTest extends Specification {

    def patternSet = Mock(PatternSet)
    def processor = Mock(StaleClassProcessor)
    def dependencyInfo = Mock(ClassDependencyInfo)

    def "does nothing when no input classes"() {
        def info = new DefaultRebuildInfo([])
        0 * _._
        expect:
        info.configureCompilation(patternSet, processor) == RebuildInfo.Info.Incremental
    }

    def "does not configures compilation when full rebuild required"() {
        def info = new DefaultRebuildInfo(null)
        0 * _._
        expect:
        info.configureCompilation(patternSet, processor) == RebuildInfo.Info.FullRebuild
    }

    def "configures compilation"() {
        def info = new DefaultRebuildInfo(['Bar', 'com.foo.Foo'])

        when:
        def i = info.configureCompilation(patternSet, processor)

        then:
        i == RebuildInfo.Info.Incremental

        1 * patternSet.include("Bar.java")
        1 * patternSet.include("com/foo/Foo.java")

        1 * processor.addStaleClass('Bar')
        1 * processor.addStaleClass('com.foo.Foo')

        0 * _._
    }
}
