/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.notations

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParserBuilder
import spock.lang.Specification

public class DependencyClassPathNotationConverterTest extends Specification {
    def instantiator = Mock(Instantiator.class)
    def classPathRegistry = Mock(ClassPathRegistry.class)
    def fileResolver = Mock(FileResolver.class)
    def factory = new DependencyClassPathNotationConverter(instantiator, classPathRegistry, fileResolver)

    def "parses classpath literals"() {
        given:
        def dependency = Mock(SelfResolvingDependency.class)
        def fileCollection = Mock(FileCollectionInternal)
        def classpath = Mock(ClassPath.class)
        def files = [new File('foo')]

        and:
        classPathRegistry.getClassPath('GRADLE_API') >> classpath
        classpath.asFiles >> files
        fileResolver.resolveFiles(files) >> fileCollection
        instantiator.newInstance(DefaultSelfResolvingDependency.class, fileCollection as Object) >> dependency

        when:
        def out = parse(DependencyFactory.ClassPathNotation.GRADLE_API)

        then:
        out.is dependency

        when: // same instance is reused
        def out2 = parse(DependencyFactory.ClassPathNotation.GRADLE_API)

        then:
        0 * instantiator._
        out2.is out
    }

    def parse(def value) {
        return NotationParserBuilder.toType(Dependency).fromType(DependencyFactory.ClassPathNotation, factory).toComposite().parseNotation(value)
    }

}


