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
package org.gradle.integtests

import org.gradle.integtests.fixtures.ArtifactBuilder
import org.junit.Test
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest

public class ExternalPluginIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void canReferencePluginInBuildSrcProjectById() {
        testFile('buildSrc/src/main/java/CustomPlugin.java') << '''
import org.gradle.api.*;
public class CustomPlugin implements Plugin<Project> {
    public void apply(Project p) { p.setProperty("prop", "value"); }
}
'''
        testFile('buildSrc/src/main/resources/META-INF/gradle-plugins/custom.properties') << '''
implementation-class=CustomPlugin
'''

        testFile('build.gradle') << '''
apply plugin: 'custom'
assert 'value' == prop
task test
'''
        inTestDirectory().withTasks('test').run()
    }
    
    @Test
    public void canReferencePluginInExternalJarById() {
        ArtifactBuilder builder = artifactBuilder()
        builder.sourceFile('CustomPlugin.java') << '''
import org.gradle.api.*;
public class CustomPlugin implements Plugin<Project> {
    public void apply(Project p) { p.setProperty("prop", "value"); }
}
'''
        builder.resourceFile('META-INF/gradle-plugins/custom.properties') << '''
implementation-class=CustomPlugin
'''
        builder.buildJar(testFile('external.jar'))

        testFile('build.gradle') << '''
buildscript {
    dependencies {
        classpath files('external.jar')
    }
}
apply plugin: 'custom'
assert 'value' == prop
task test
'''
        inTestDirectory().withTasks('test').run()
    }
}
