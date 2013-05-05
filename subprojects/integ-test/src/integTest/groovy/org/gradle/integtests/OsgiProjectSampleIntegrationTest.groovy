/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.Test

import java.util.jar.Manifest

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * @author Hans Dockter
 */
class OsgiProjectSampleIntegrationTest extends AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'osgi')

    @Test
    public void osgiProjectSamples() {
        long start = System.currentTimeMillis()
        TestFile osgiProjectDir = sample.dir
        executer.inDirectory(osgiProjectDir).withTasks('clean', 'assemble').run()
        TestFile tmpDir = testDirectory
        osgiProjectDir.file('build/libs/osgi-1.0.jar').unzipTo(tmpDir)
        tmpDir.file('META-INF/MANIFEST.MF').withInputStream { InputStream instr ->
            Manifest manifest = new Manifest(instr)
            checkManifest(manifest, start)
        }
    }

    static void checkManifest(Manifest manifest, start) {
        assertEquals('Example Gradle Activator', manifest.mainAttributes.getValue('Bundle-Name'))
        assertEquals('2', manifest.mainAttributes.getValue('Bundle-ManifestVersion'))
        assertEquals('Bnd-1.50.0', manifest.mainAttributes.getValue('Tool'))
        assertTrue(start <= Long.parseLong(manifest.mainAttributes.getValue('Bnd-LastModified')))
        assertEquals('1.0.0', manifest.mainAttributes.getValue('Bundle-Version'))
        assertEquals('gradle_tooling.osgi', manifest.mainAttributes.getValue('Bundle-SymbolicName'))
        assertEquals( GradleVersion.current().version, manifest.mainAttributes.getValue('Built-By'))
    }
}
