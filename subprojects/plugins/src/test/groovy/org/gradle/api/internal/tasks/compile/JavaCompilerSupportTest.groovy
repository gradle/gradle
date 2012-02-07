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
package org.gradle.api.internal.tasks.compile

import spock.lang.Specification
import org.gradle.api.tasks.WorkResult
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.compile.CompileOptions

class JavaCompilerSupportTest extends Specification {
    def compiler = new JavaCompilerSupport() {
        WorkResult execute() {
            null
        }
    }

    def "provides access to configuration options"() {
        when:
        compiler.with {
            source = new SimpleFileCollection(new File("Person.java"))
            destinationDir = new File("out")
            classpath = [new File("lib.jar")]
            compileOptions = new CompileOptions().with() { encoding = 'utf-8'; it }
            sourceCompatibility = "1.5"
            targetCompatibility = "1.6"
            dependencyCacheDir = new File("cache")
        }
        
        then:
        compiler.source.singleFile.name == "Person.java"
        compiler.destinationDir.name == "out"
        compiler.classpath[0].name == "lib.jar"
        compiler.compileOptions.encoding == "utf-8"
        compiler.sourceCompatibility == "1.5"
        compiler.targetCompatibility == "1.6"
        compiler.dependencyCacheDir.name == "cache"
    }
    
    def "can configure another compiler"() {
        def other = Mock(JavaCompiler)
        
        when:
        compiler.configure(other)
        
        then:
        1 * other.setSource(_)
        1 * other.setDestinationDir(_)
        1 * other.setClasspath(_)
        1 * other.setCompileOptions(_)
        1 * other.setSourceCompatibility(_)
        1 * other.setTargetCompatibility(_)
        1 * other.setDependencyCacheDir(_)
    }
}
