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
package org.gradle.api.internal.file.copy

import org.gradle.api.file.FileTree
import org.gradle.api.internal.Actions
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

public class CopyActionImplTest extends Specification {
    FileCopySpecContentVisitor visitor = Mock()
    FileResolver resolver = Mock()
    FileTree sourceFileTree = Mock()
    Instantiator instantiator = new DirectInstantiator()
    CopyActionImpl copyAction = new CopyActionImpl(instantiator, resolver, visitor, Actions.doNothing())

    def delegatesToMainSpecRootSpec() {
        when:
        copyAction.include 'a'

        then:
        copyAction.includes == ['a'] as Set
        copyAction.mainSpec.includes == ['a'] as Set
        copyAction.rootSpec.includes == [] as Set
        copyAction.rootSpec.childSpecs.contains(copyAction.mainSpec)
    }

    def didWorkDelegatesToVisitor() {
        when:
        def didWork = copyAction.didWork

        then:
        1 * visitor.didWork >> true
        didWork
    }

    def visitsAndCopiesEachSpec() {
        FileTree source = Mock()
        _ * source.matching(_) >> source

        copyAction.from('source1')
        def child = copyAction.from('source2') {}

        when:
        copyAction.execute()

        then:
        1 * visitor.startVisit(copyAction)
        1 * visitor.visitSpec(copyAction.rootSpec)
        1 * resolver.resolveFilesAsTree([[] as Set] as Object[]) >> source
        1 * visitor.visitSpec(copyAction.mainSpec)
        1 * resolver.resolveFilesAsTree([['source1'] as Set] as Object[]) >> source
        1 * visitor.visitSpec(child)
        1 * resolver.resolveFilesAsTree([['source2'] as Set] as Object[]) >> source
        1 * visitor.endVisit()
        0 * resolver._
        0 * visitor._
    }

    def allSourceIncludesSourceFromAllSpecs() {
        FileTree mainSource = Mock()
        _ * mainSource.matching(_) >> mainSource
        FileTree rootSource = Mock()
        _ * rootSource.matching(_) >> rootSource
        FileTree childSource = Mock()
        _ * childSource.matching(_) >> childSource
        FileTree allSource = Mock()

        copyAction.from('source1')
        copyAction.from('source2') {}

        when:
        def source = copyAction.allSource

        then:
        source == allSource
        1 * resolver.resolveFilesAsTree([[] as Set] as Object[]) >> rootSource
        1 * resolver.resolveFilesAsTree([['source1'] as Set] as Object[]) >> mainSource
        1 * resolver.resolveFilesAsTree([['source2'] as Set] as Object[]) >> childSource
        1 * resolver.resolveFilesAsTree([[rootSource, mainSource, childSource]] as Object[]) >> allSource
        0 * resolver._
        0 * visitor._
    }
}
