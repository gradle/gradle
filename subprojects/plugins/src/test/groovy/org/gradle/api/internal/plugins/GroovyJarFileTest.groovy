/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.plugins

import spock.lang.Specification

class GroovyJarFileTest extends Specification {
    def "parse non-Groovy file"() {
        expect:
        GroovyJarFile.parse(new File("groovy-other-2.0.5.jar")) == null
        GroovyJarFile.parse(new File("groovy-2.0.5.zip")) == null
    }

    def "parse 'groovy' Jar"() {
        def jar = GroovyJarFile.parse(new File("/lib/groovy-2.0.5.jar"))

        expect:
        jar != null
        jar.file == new File("/lib/groovy-2.0.5.jar")
        jar.baseName == "groovy"
        jar.version.toString() == "2.0.5"
        !jar.groovyAll
        !jar.indy
        jar.dependencyNotation == "org.codehaus.groovy:groovy:2.0.5"
    }

    def "parse 'groovy-all' Jar"() {
        def jar = GroovyJarFile.parse(new File("/lib/groovy-all-2.0.5.jar"))

        expect:
        jar != null
        jar.file == new File("/lib/groovy-all-2.0.5.jar")
        jar.baseName == "groovy-all"
        jar.version.toString() == "2.0.5"
        jar.groovyAll
        !jar.indy
        jar.dependencyNotation == "org.codehaus.groovy:groovy-all:2.0.5"

    }

    def "parse indy Jar"() {
        def jar = GroovyJarFile.parse(new File("/lib/groovy-2.0.5-indy.jar"))

        expect:
        jar != null
        jar.file == new File("/lib/groovy-2.0.5-indy.jar")
        jar.baseName == "groovy"
        jar.version.toString() == "2.0.5"
        !jar.groovyAll
        jar.indy
        jar.dependencyNotation == "org.codehaus.groovy:groovy:2.0.5:indy"
    }
}
