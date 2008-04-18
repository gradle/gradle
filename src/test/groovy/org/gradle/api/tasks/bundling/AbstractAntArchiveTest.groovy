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

import org.gradle.api.tasks.util.AntDirective
import org.gradle.api.tasks.util.FileSet
import org.gradle.api.tasks.util.FileCollection
import org.gradle.util.HelperUtil

/**
* @author Hans Dockter
*/
abstract class AbstractAntArchiveTest extends GroovyTestCase {
    static final String METAINFS_KEY = 'mymetainfs'

    File testDir
    File txtFile
    File jpgFile
    File xmlFile
    File groovyFile
    File gradleFile
    File unzipDir
    String archiveName
    FileSet fileSet
    FileCollection fileCollection
    List resourceCollections

    GradleManifest manifest
    File manifestFile
    Map mainAttributes
    Map sectionAttributes
    String sectionName
    String manifestFileKey
    String manifestFileValue

    Map fileSetDuos = [:]
    Map fileSetDuosFiles = [:]

    void setUp() {
        testDir = HelperUtil.makeNewTestDir()
        (unzipDir = new File(testDir, 'unzipDir')).mkdir()
        archiveName = 'test.jar'

        fileSet = new FileSet(testDir)
        fileSet.include('**/*.txt', '**/*.jpg')
        fileSet.exclude('**/*.jpg')
        createTestFiles()
        createMetaData()
        fileCollection = new FileCollection()
        fileCollection.files = [gradleFile]
        AntDirective antDirective = new AntDirective()
        antDirective.directive = {
            files(includes: groovyFile.absolutePath) {

            }
        }
        resourceCollections = [fileSet, fileCollection, antDirective]
    }
    private void createMetaData() {
        mainAttributes = [attr1: 'value1', attr2: 'value2']
        sectionAttributes = [sectionAttr1: 'value1', sectionAttr2: 'value2']
        sectionName = 'section1'
        manifest = new GradleManifest()
        manifest.file = manifestFile
        manifest.mainAttributes(mainAttributes)
        manifest.sections(sectionAttributes, sectionName)
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void checkResourceFiles() {
        assertTrue(new File(unzipDir, txtFile.name).exists())
        assertTrue(new File(unzipDir, gradleFile.name).exists())
        assertTrue(new File(unzipDir, groovyFile.name).exists())
        assertFalse(new File(unzipDir, xmlFile.name).exists())
        assertFalse(new File(unzipDir, jpgFile.name).exists())
    }

    void checkMetaData() {
        java.util.jar.Manifest manifestProperties = new java.util.jar.Manifest(
                new FileInputStream(new File(unzipDir, 'META-INF/MANIFEST.MF')))
        assertEquals(manifestFileValue, manifestProperties.mainAttributes.getValue(manifestFileKey))
        mainAttributes.each {String key, String value ->
            assertEquals(value, manifestProperties.mainAttributes.getValue(key))
        }
        sectionAttributes.each {String key, String value ->
            assertEquals(value, manifestProperties.entries[sectionName].getValue(key))
        }
        checkExistenceOfFileSetDuo(unzipDir, 'META-INF/', METAINFS_KEY)
    }

    void unzipArchive(String antTaskName = null, Compression compression = null) {
        AntBuilder ant = new AntBuilder()
        Map args = [src: new File(testDir, archiveName), dest: unzipDir]
        if (compression) { args.compression = compression.antValue }
        ant."${antTaskName ?: 'unzip'}"(args)
    }

    protected void createTestFiles() {
        manifestFileKey = 'file'
        manifestFileValue = 'value'
        (txtFile = new File(testDir, 'test.txt')).createNewFile()
        (jpgFile = new File(testDir, 'test.jpg')).createNewFile()
        (xmlFile = new File(testDir, 'test.xml')).createNewFile()
        (gradleFile = new File(testDir, 'test.gradle')).createNewFile()
        (groovyFile = new File(testDir, 'test.groovy')).createNewFile()
        (manifestFile = new File(testDir, 'MANIFEST.MF')).write("$manifestFileKey: $manifestFileValue")
    }

    List createFileSetDuo(String key) {
        File file1 = new File(testDir, "${key}file1")
        file1.createNewFile()
        File file2 = new File(testDir, "${key}file2")
        file2.createNewFile()
        fileSetDuosFiles[key] = [file1, file2]
        FileSet fileSet1 = new FileSet(testDir)
        fileSet1.include(file1.name)
        FileSet fileSet2 = new FileSet(testDir)
        fileSet2.include(file2.name)
        fileSetDuos[key] = [fileSet1, fileSet2]
        [fileSet1, fileSet2]
    }

    void checkExistenceOfFileSetDuo(File baseDir, String pathPrefix, String key) {
        fileSetDuosFiles[key].each {
            assertTrue(new File(baseDir, "$pathPrefix$it.name").exists())
        }
    }

}


