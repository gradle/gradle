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
package org.gradle.integtests.plugins

import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*

import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.junit.Before
import org.junit.Test


class JDependIntegrationTest extends AbstractIntegrationTest {
    @Before
    void newBuild() {
        writeBuildFile()
    }
    
    @Test
    void check_EmptyProject_Success() {
        inTestDirectory().withTasks('check').run()
    }
    
    @Test
    void check_WithSource_GenerateReport() {
        testFile('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { public boolean is() { return true; } }'
        testFile('src/test/java/org/gradle/Class1Test.java') << 'package org.gradle; class Class1Test { public boolean equals(Object arg) { return true; } }'
        
        inTestDirectory().withTasks('check').run()
        
        testFile('build/jdepend/main.xml').assertContents(containsString('org.gradle.Class1'))
        testFile('build/jdepend/test.xml').assertContents(containsString('org.gradle.Class1Test'))
    }
    
    private void writeBuildFile() {
        testFile('build.gradle') << """\
        apply plugin: 'groovy'
        apply plugin: 'jdepend'
        
        repositories {
            mavenCentral()
        }
        
        dependencies {
            jdepend group:'jdepend', name:'jdepend', version:'2.9.1'
            jdepend group:'org.apache.ant', name:'ant-jdepend', version:'1.7.1'
        }
        """
    }
}
