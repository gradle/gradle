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
package org.gradle.api.java.archives.internal

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeDetails
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification


class DefaultManifestMergeSpecTest extends Specification {
    def static final MANIFEST_VERSION_MAP = ['Manifest-Version': '1.0']

    DefaultManifestMergeSpec mergeSpec = new DefaultManifestMergeSpec()
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def from() {
        expect:
        mergeSpec.from('some') == mergeSpec
        mergeSpec.from('someOther')
        mergeSpec.mergePaths == ['some', 'someOther']
    }

    def mergeWithFileAndObjectManifest() {
        def fileResolver = Mock(FileResolver)
        DefaultManifest baseManifest = new DefaultManifest(fileResolver)
        def baseMap = baseManifest.attributes + ([key1: 'value1', key2: 'value2', key3: 'value3'] as LinkedHashMap)
        def baseSectionMap = [keysec1: 'valueSec1', keysec2: 'valueSec2', keysec3: 'valueSec3']
        baseManifest.attributes(baseMap)
        baseManifest.attributes(baseSectionMap, 'section')

        DefaultManifest otherManifest = new DefaultManifest(fileResolver)
        def otherMap = [key1: 'value1other', key4: 'value4other', key5: 'value5other'] as LinkedHashMap
        def otherSectionMap = [keysec1: 'value1Secother', keysec4: 'value4Secother', keysec5: 'value5Secother']
        otherManifest.attributes(otherMap)
        otherManifest.attributes(otherSectionMap, 'section')

        TestFile manifestFile = tmpDir.file('somefile')
        fileResolver.resolve(manifestFile) >> manifestFile
        DefaultManifest fileManifest = new DefaultManifest(fileResolver)
        def fileMap = [key2: 'value2File', key4: 'value4File', key6: 'value6File'] as LinkedHashMap
        def fileSectionMap = [keysec2: 'value2Secfile', keysec4: 'value5Secfile', keysec6: 'value6Secfile']
        fileManifest.attributes(fileMap)
        fileManifest.attributes(fileSectionMap, 'section')
        fileManifest.writeTo(manifestFile)

        when:
        mergeSpec.from(otherManifest, manifestFile)
        DefaultManifest mergedManifest = mergeSpec.merge(baseManifest, fileResolver)

        then:
        mergedManifest.attributes == baseMap + otherMap + fileMap
        mergedManifest.attributes.keySet() as List == ['Manifest-Version', 'key1', 'key2', 'key3', 'key4', 'key5', 'key6']
        mergedManifest.sections.section == baseSectionMap + otherSectionMap + fileSectionMap
        mergedManifest.sections.section.keySet() as List == ['keysec1', 'keysec2', 'keysec3', 'keysec4', 'keysec5', 'keysec6']
    }

    def mergeWithExcludeAction() {
        DefaultManifest baseManifest = new DefaultManifest(Mock(FileResolver))
        baseManifest.attributes key1: 'value1', key2: 'value2'
        mergeSpec.from(new DefaultManifest(Mock(FileResolver)))
        mergeSpec.eachEntry {ManifestMergeDetails details ->
            if (details.getKey() == 'key2') {
                details.exclude()
            }
        }

        expect:
        mergeSpec.merge(baseManifest, Mock(FileResolver)).attributes == [key1: 'value1'] + MANIFEST_VERSION_MAP
    }

    def mergeWithNestedFrom() {
        DefaultManifest baseManifest = new DefaultManifest(Mock(FileResolver)).attributes(key1: 'value1')
        DefaultManifest mergeManifest = new DefaultManifest(Mock(FileResolver)).attributes(key2: 'value2')
        DefaultManifest nestedMergeManifest = new DefaultManifest(Mock(FileResolver)).attributes(key3: 'value3')
        mergeManifest.from(nestedMergeManifest)
        mergeSpec.from(mergeManifest)

        expect:
        mergeSpec.merge(baseManifest, Mock(FileResolver)).attributes == [key1: 'value1', key2: 'value2', key3: 'value3'] +
                MANIFEST_VERSION_MAP
    }

    def mergeWithChangeValueAction() {
        DefaultManifest baseManifest = new DefaultManifest(Mock(FileResolver))
        baseManifest.attributes key1: 'value1', key2: 'value2'
        mergeSpec.from(new DefaultManifest(Mock(FileResolver)))
        mergeSpec.eachEntry {ManifestMergeDetails details ->
            if (details.getKey() == 'key2') {
                details.setValue('newValue2')
            }
        }

        expect:
        mergeSpec.merge(baseManifest, Mock(FileResolver)).attributes == [key1: 'value1', key2: 'newValue2'] + MANIFEST_VERSION_MAP
    }

    def mergeWithActionOnSection() {
        DefaultManifest baseManifest = new DefaultManifest(Mock(FileResolver))
        baseManifest.attributes('section', key1: 'value1')
        mergeSpec.from(new DefaultManifest(Mock(FileResolver)).attributes('section', key2: 'mergeValue2'))
        mergeSpec.eachEntry {ManifestMergeDetails details ->
            if (details.getBaseValue() != null && details.getMergeValue() == null) {
                details.exclude();
            }
        }

        expect:
        mergeSpec.merge(baseManifest, Mock(FileResolver)).sections.section == [key2: 'mergeValue2']
    }

    def mergeWithForeignManifest() {
        DefaultManifest baseManifest = new DefaultManifest(Mock(FileResolver))
        baseManifest.attributes key1: 'value1'
        Manifest foreign = new CustomManifestInternalWrapper(new DefaultManifest(Mock(FileResolver)).attributes( key2: 'value2'))
        mergeSpec.from(foreign)

        expect:
        mergeSpec.merge(baseManifest, Mock(FileResolver)).attributes == [key1: 'value1', key2: 'value2'] + MANIFEST_VERSION_MAP
    }

    def mergeShouldWinByDefault() {
        DefaultManifest baseManifest = new DefaultManifest(Mock(FileResolver))
        baseManifest.attributes key1: 'value1'
        mergeSpec.from(new DefaultManifest(Mock(FileResolver)).attributes(key1: 'mergeValue1'))

        expect:
        mergeSpec.merge(baseManifest, Mock(FileResolver)).attributes == [key1: 'mergeValue1'] + MANIFEST_VERSION_MAP
    }

    def addActionByAnonymousInnerClass() {
        DefaultManifest baseManifest = new DefaultManifest(Mock(FileResolver))
        mergeSpec.from(new DefaultManifest(Mock(FileResolver)).attributes(key1: 'value1'))
        mergeSpec.eachEntry(new Action() {
            void execute(def mergeDetails) {
                if (mergeDetails.key == 'key1') {
                    mergeDetails.exclude()
                }
            }
        })

        expect:
        mergeSpec.merge(baseManifest, Mock(FileResolver)).attributes == MANIFEST_VERSION_MAP
    }
}
