/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.javadoc


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

@Requires(TestPrecondition.JDK11_OR_LATER) //not being able to resolve dependencies is a hard fail for the javadoc tool only since Java 11
class JavadocMultipleSubprojectsIntegrationTest extends AbstractIntegrationSpec {

    def "default javadoc task works"() {
        given:
        setupProject('')

        when:
        succeeds('javadoc')

        then:
        file('app/build/docs/javadoc/com/test/Foo.html').exists()
    }

    @Ignore //test currently fails, because the custom Javadoc task ends up with an empty classpath
    def "custom javadoc task works"() {
        given:
        setupProject("""
            task myJavadocs(type: Javadoc) {
                source = sourceSets.main.allJava
            }
        """)

        when:
        succeeds('myJavadocs')

        then:
        file('app/build/docs/javadoc/Foo.html').exists()
    }

    private void setupProject(String extraAppBuildFileContent) {
        buildFile.delete()

        settingsFile << """
            rootProject.name = 'rootProject'
            include('app', 'util')
        """

        file('app/build.gradle') << """
            plugins {
                id 'java'
            }
            
            dependencies {
                implementation project(':util')
            }
            
            $extraAppBuildFileContent
        """
        file('app/src/main/java/com/test/Foo.java') << """
            package com.test;

            import com.test.Util;

            public class Foo {
                private final Class c = Util.class;
            }
        """

        file('util/build.gradle') << """
            plugins {
                id 'java'
            }
        """
        file('util/src/main/java/Util.java') << '''
            package com.test;

            public class Util {}
        '''
    }

}
