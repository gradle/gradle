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

import org.gradle.util.VersionNumber
import spock.lang.Specification
import spock.lang.Unroll

class GroovyJarFileTest extends Specification {
    def "parse non-Groovy file"() {
        expect:
        GroovyJarFile.parse(new File("groovy-other-2.0.5.jar")) == null
        GroovyJarFile.parse(new File("groovy-1b3e1k20f5.jar")) == null
        GroovyJarFile.parse(new File("groovy-2.0.5.zip")) == null
    }

    @Unroll
    def "parse 'groovy' Jar with for #dependency"() {
        def jar = GroovyJarFile.parse(new File(path))

        expect:
        jar != null
        jar.file == new File(path)
        jar.baseName == baseName
        jar.version == VersionNumber.parse(version)
        jar.groovyAll == isAll
        jar.indy == isIndy
        jar.dependencyNotation == dependency

        
        where:
        path                                                            | baseName     | version                                    | isAll | isIndy | dependency
        "/lib/groovy-2.0.5.jar"                                         | "groovy"     | "2.0.5"                                    | false | false  | "org.codehaus.groovy:groovy:2.0.5"
        "/lib/groovy-1b3e1520f5.jar"                                    | "groovy"     | "1b3e1520f5"                               | false | false  | "org.codehaus.groovy:groovy:1b3e1520f5"
        "/lib/groovy-ea60e33cb2fd4b66ae3c6e87200005fa941dca2c.jar"      | "groovy"     | "ea60e33cb2fd4b66ae3c6e87200005fa941dca2c" | false | false  | "org.codehaus.groovy:groovy:ea60e33cb2fd4b66ae3c6e87200005fa941dca2c"
        "/lib/groovy-all-2.0.5.jar"                                     | "groovy-all" | "2.0.5"                                    | true  | false  | "org.codehaus.groovy:groovy-all:2.0.5"
        "/lib/groovy-all-1b3e1520f5.jar"                                | "groovy-all" | "1b3e1520f5"                               | true  | false  | "org.codehaus.groovy:groovy-all:1b3e1520f5"
        "/lib/groovy-all-ea60e33cb2fd4b66ae3c6e87200005fa941dca2c.jar"  | "groovy-all" | "ea60e33cb2fd4b66ae3c6e87200005fa941dca2c" | true  | false  | "org.codehaus.groovy:groovy-all:ea60e33cb2fd4b66ae3c6e87200005fa941dca2c"
        "/lib/groovy-2.0.5-indy.jar"                                    | "groovy"     | "2.0.5"                                    | false | true   | "org.codehaus.groovy:groovy:2.0.5:indy"
        "/lib/groovy-1b3e1520f5-indy.jar"                               | "groovy"     | "1b3e1520f5"                               | false | true   | "org.codehaus.groovy:groovy:1b3e1520f5:indy"
        "/lib/groovy-ea60e33cb2fd4b66ae3c6e87200005fa941dca2c-indy.jar" | "groovy"     | "ea60e33cb2fd4b66ae3c6e87200005fa941dca2c" | false | true   | "org.codehaus.groovy:groovy:ea60e33cb2fd4b66ae3c6e87200005fa941dca2c:indy"
    }
}
