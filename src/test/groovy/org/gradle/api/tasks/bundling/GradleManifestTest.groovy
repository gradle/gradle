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

package org.gradle.api.tasks.bundling

import java.util.jar.Manifest

/**
* @author Hans Dockter
*/
class GradleManifestTest extends GroovyTestCase {
    GradleManifest gradleManifest = new GradleManifest()

    void setUp() {
        gradleManifest = new GradleManifest()
    }

    void testInit() {
        Manifest baseManifest = new Manifest()
        GradleManifest gradleManifest = new GradleManifest(baseManifest)
        assert gradleManifest.baseManifest.is(baseManifest)
    }

    void testGradleManifest() {
        assertNull(gradleManifest.file)
        assertNotNull(gradleManifest.manifest)
    }

    void testAddMainAttributes() {
        Map attributes = [key1: 'value1', key2: 'value2']
        Map attributes2 = [key3: 'value3']
        assert gradleManifest.is(gradleManifest.mainAttributes(attributes))
        gradleManifest.mainAttributes(attributes2)

        Manifest manifest = gradleManifest.manifest
        assertEquals(3, manifest.mainAttributes.size())
        (attributes + attributes2).each {String key, String value ->
            assertEquals(value, manifest.mainAttributes.getValue(key))       
        }
    }

    void testAddSectionAttributes() {
        String section1 = 'section1'
        String section2 = 'section2'
        Map attributes = [key1: 'value1', key2: 'value2']
        Map attributes2 = [key3: 'value3']
        Map attributes3 = [key3: 'value3']
        assert gradleManifest.is(gradleManifest.sections(attributes, section1))
        gradleManifest.sections(attributes2, section1)
        gradleManifest.sections(attributes3, section2)

        Manifest manifest = gradleManifest.manifest
        assertEquals(3, manifest.entries[section1].size())
        (attributes + attributes2).each {String key, String value ->
            assertEquals(value, manifest.entries[section1].getValue(key))
        }

        assertEquals(1, manifest.entries[section2].size())
        attributes3.each {String key, String value ->
            assertEquals(value, manifest.entries[section2].getValue(key))
        }
    }

    void testAddToAntBuilder() {
        // todo: manifest behaves differently when used within a jar or outside a jar. The commented out approach does not work
//        HelperUtil.makeNewTestDir()
//        gradleManifest.addToAntBuilder(new AntBuilder().jar(destfile: new File(HelperUtil.TMP_DIR_FOR_TEST, 'somedest').absolutePath))
//        HelperUtil.deleteTestDir()
    }
}
