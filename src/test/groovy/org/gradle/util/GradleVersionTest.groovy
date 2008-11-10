/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.util

import org.codehaus.groovy.runtime.InvokerHelper
import static org.junit.Assert.*
import org.junit.Test
import org.apache.tools.ant.Main
import org.apache.ivy.Ivy;

/**
 * @author Hans Dockter
 */
class GradleVersionTest {
    @Test public void testGradleVersion() {
        GradleVersion gradleVersion = new GradleVersion()
        assertEquals(TestConsts.VERSION, gradleVersion.version)
        assertEquals(TestConsts.BUILD_TIME, gradleVersion.buildTime)
    }

    @Test public void testPrettyPrint() {
        String expectedText = """
----------------------------------------
Gradle $TestConsts.VERSION
----------------------------------------

Gradle buildtime: $TestConsts.BUILD_TIME
Groovy: $InvokerHelper.version
Ant: $Main.antVersion
Ivy: ${Ivy.ivyVersion}
Java: ${System.getProperty("java.version")}
JVM: ${System.getProperty("java.vm.version")}
JVM Vendor: ${System.getProperty("java.vm.vendor")}
OS Name: ${System.getProperty("os.name")}
"""
        assertEquals(expectedText, new GradleVersion().prettyPrint())
    }
}
