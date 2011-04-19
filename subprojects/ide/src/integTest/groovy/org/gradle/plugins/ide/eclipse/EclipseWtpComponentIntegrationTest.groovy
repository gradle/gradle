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

package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test

/**
 * Author: Szczepan Faber, created at: 4/19/11
 */
class EclipseWtpComponentIntegrationTest extends AbstractEclipseIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources()

    String content

    @Test
    void allowsConfiguringEclipseProject() {
        //given
        file('someExtraSourceDir').mkdirs()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'eclipse'

eclipse {
  wtp {
    sourceDirs += file('someExtraSourceDir')
  }
}
        """

        def component = getFile([:], '.settings/org.eclipse.wst.common.component').text
        println component //TODO SF after completing the refactoring, get rid of the printlns

        //then
        assert component.contains('someExtraSourceDir')
    }

    protected def contains(String ... contents) {
        contents.each { assert content.contains(it)}
    }
}
