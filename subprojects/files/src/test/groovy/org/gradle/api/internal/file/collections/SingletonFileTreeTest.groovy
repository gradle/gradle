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
package org.gradle.api.internal.file.collections

import org.gradle.util.UsesNativeServices
import spock.lang.Specification
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.FileVisitDetails

@UsesNativeServices
class SingletonFileTreeTest extends Specification {
    def hasUsefulDisplayName() {
        File f = new File('test-file')
        SingletonFileTree tree = new SingletonFileTree(f)

        expect:
        tree.displayName == "file '$f'"
    }
    
    def visitsFileAsChildOfRoot() {
        FileVisitor visitor = Mock()
        File f = new File('test-file')
        SingletonFileTree tree = new SingletonFileTree(f)

        when:
        tree.visit(visitor)

        then:
        1 * visitor.visitFile(!null) >> { FileVisitDetails details ->
            assert details.file == f
            assert details.path == 'test-file'
        }
        0 * visitor._
    }
}
