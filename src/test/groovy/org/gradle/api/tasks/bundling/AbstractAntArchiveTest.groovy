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
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.GradleManifest
import org.gradle.api.tasks.util.AntDirective
import org.gradle.api.tasks.util.FileSet
import org.gradle.api.tasks.util.TarFileSet
import org.gradle.api.tasks.util.ZipFileSet
import org.gradle.util.HelperUtil
import org.junit.After
import org.junit.Before
import static org.junit.Assert.*

/**
* @author Hans Dockter
*/
abstract class AbstractAntArchiveTest {
    static final String METAINFS_KEY = 'mymetainfs'

    File testDir
    File txtFile
    File jpgFile
    File xmlFile
    File groovyFile
    File unzipDir
    String archiveName
    FileSet fileSet
    FileSet mergeGroupFileSet
    List mergeGroupFileSets
    FileSet mergeZipFileSet
    FileSet mergeTarFileSet
    List mergeFileSets
    List resourceCollections

    File mergeZipFile
    File mergeGroupFile
    File mergeTarFile
    File zipGroupContentFile
    File zipContentFile
    File tarContentFile

    GradleManifest manifest
    File manifestFile
    Map mainAttributes
    Map sectionAttributes
    String sectionName
    String manifestFileKey
    String manifestFileValue

    Map fileSetDuos = [:]
    Map fileSetDuosFiles = [:]

    @Before public void setUp()  {
        testDir = HelperUtil.makeNewTestDir()
        mergeGroupFile = new File(testDir, 'test_mergegroup.zip')
        mergeZipFile = new File(testDir, 'test_merge_zip.zip')
        mergeTarFile = new File(testDir, 'test_merge_tar.tar')

        (unzipDir = new File(testDir, 'unzipDir')).mkdir()
        archiveName = 'test.jar'

        mergeGroupFileSet = new FileSet(testDir)
        mergeGroupFileSet.include("$mergeGroupFile.name")
        mergeGroupFileSets = [mergeGroupFileSet]

        mergeZipFileSet = new ZipFileSet(mergeZipFile)
        mergeTarFileSet = new TarFileSet(mergeTarFile)
        mergeFileSets = [mergeZipFileSet, mergeTarFileSet]

        fileSet = new FileSet(testDir)
        fileSet.include('**/*.txt', '**/*.jpg')
        fileSet.exclude('**/*.jpg')
        createTestFiles()
        createMetaData()
        AntDirective antDirective = new AntDirective()
        antDirective.directive = {
            files(includes: groovyFile.absolutePath) {

            }
        }
        resourceCollections = [fileSet, antDirective]
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

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void checkResourceFiles() {
        assertTrue(new File(unzipDir, txtFile.name).exists())
        assertTrue(new File(unzipDir, groovyFile.name).exists())
        assertTrue(new File(unzipDir, zipContentFile.name).exists())
        assertTrue(new File(unzipDir, zipGroupContentFile.name).exists())
        assertTrue(new File(unzipDir, tarContentFile.name).exists())
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
        (groovyFile = new File(testDir, 'test.groovy')).createNewFile()
        (manifestFile = new File(testDir, 'MANIFEST.MF')).write("$manifestFileKey: $manifestFileValue")
        createArchiveFiles()
    }

    protected void createArchiveFiles() {
        (zipContentFile = new File(testDir, 'zipcontent.txt')).createNewFile()
        (zipGroupContentFile = new File(testDir, 'zipgroupcontent.txt')).createNewFile()
        (tarContentFile = new File(testDir, 'tarcontent.txt')).createNewFile()
        AntBuilder ant = new AntBuilder()
        ant.zip(destfile: mergeGroupFile, basedir: testDir, includes: "$zipGroupContentFile.name")
        ant.zip(destfile: mergeZipFile, basedir: testDir, includes: "$zipContentFile.name")
        ant.tar(destfile: mergeTarFile, basedir: testDir, includes: "$tarContentFile.name")
        zipContentFile.delete()
        zipGroupContentFile.delete()
        tarContentFile.delete()
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


