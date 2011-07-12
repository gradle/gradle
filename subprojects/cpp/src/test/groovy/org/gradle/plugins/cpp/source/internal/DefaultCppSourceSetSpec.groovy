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
package org.gradle.plugins.cpp.source.internal

import org.gradle.api.internal.file.FileResolver
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile

import org.junit.Rule
import spock.lang.*

class DefaultCppSourceSetSpec extends Specification {

    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    final FileResolver fileResolver = [resolve: { tmpDir.file(it) }] as FileResolver
    
    @Delegate DefaultCppSourceSet sourceSet = sourceSet('main')
    
    DefaultCppSourceSet sourceSet(String name) {
        new DefaultCppSourceSet(name, fileResolver)
    }
    
    TestFile file(String... path) {
        tmpDir.file(path)
    }
    
    def "has nice to string"() {
        expect:
        sourceSet("some-thing").toString() == "C++ source set some thing"
    }
    
    def "defaults to empty source"() {
        expect:
        cpp.empty
    }
    
    def "can add cpp source"() {
        given:
        file("dir1", "some.cpp") << ""
        file("dir2", "someother.cpp") << ""
        file("dir2", "someother.xxx") << ""
        
        when:
        cpp.srcDir "dir1"
        
        then:
        cpp.singleFile.name == "some.cpp"
        
        when:
        cpp.srcDir "dir2"
        
        then:
        cpp.files*.name == ["some.cpp", "someother.cpp"] 
    }
    
    
}