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

package org.gradle.util.internal

import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import static org.gradle.util.internal.StrictJavaLocaliser.addExtension

/**
 * by Szczepan Faber, created at: 1/20/12
 */
class StrictJavaLocaliserTest extends Specification {
    
    @Rule def temp = new TemporaryFolder()

    def "validates input"() {
        when:
        new StrictJavaLocaliser(null)
        then:
        thrown(IllegalArgumentException)

        when:
        new StrictJavaLocaliser(new File("dummy"))
        then:
        thrown(IllegalArgumentException)
    }
    
    def "finds executable if for jdk style location"() {
        when:
        def home = temp.createDir("home")
        home.create {
            bin { file addExtension('java') }
            jdk {}
        }
        
        then:
        home.file(addExtension("bin/java")).absolutePath == new StrictJavaLocaliser(home.file("jdk")).getJavaExecutable("java")
    }

    def "finds executable if for jre style location"() {
        when:
        //not strictly jre-style but let's ignore it for the time being
        def home = temp.createDir("home")
        home.create {
            jre {
                bin { file addExtension('java') }
            }
        }

        then:
        home.file(addExtension("jre/bin/java")).absolutePath == new StrictJavaLocaliser(home.file("jre")).getJavaExecutable("java")
    }
    
    def "provides decent feedback if executable not found"() {
        given:
        def home = temp.createDir("home")

        when:
        new StrictJavaLocaliser(home).getJavaExecutable("foobar")
        
        then:
        thrown(StrictJavaLocaliser.JavaExecutableNotFoundException)
    }
}
