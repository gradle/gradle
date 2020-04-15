/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableSortedMap
import groovy.transform.CompileStatic
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider

@CompileStatic
trait FingerprinterFixture {
    abstract TestNameTestDirectoryProvider getTemporaryFolder()

    private DefaultFileCollectionSnapshotter snapshotter = TestFiles.fileCollectionSnapshotter()
    private AbsolutePathFileCollectionFingerprinter inputFingerprinter = new AbsolutePathFileCollectionFingerprinter(snapshotter)

    private OutputFileCollectionFingerprinter outputFingerprinter = new OutputFileCollectionFingerprinter(snapshotter)
    def getOutputFingerprinter() { outputFingerprinter }

    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintsOf(Map<String, Object> properties) {
        def builder = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>naturalOrder()
        properties.each { propertyName, value ->
            def values = (value instanceof Collection) ? (Collection<?>) value : [value]
            def files = values.collect {
                it instanceof File
                    ? it as File
                    : temporaryFolder.file(it)
            }
            def fingerprint = inputFingerprinter.fingerprint(TestFiles.fixed(files))
            builder.put(propertyName, fingerprint)
        }
        return builder.build()
    }
}
