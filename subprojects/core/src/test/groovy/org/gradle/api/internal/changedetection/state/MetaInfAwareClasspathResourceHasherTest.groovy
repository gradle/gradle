/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.file.archive.ZipEntry
import spock.lang.Specification

class MetaInfAwareClasspathResourceHasherTest extends Specification {
    def manifestHasher = Mock(ManifestFileZipEntryHasher)
    def propertiesHasher = Mock(PropertiesFileZipEntryHasher)
    def delegate = Mock(ResourceHasher)

    def hasher = new MetaInfAwareClasspathResourceHasher(delegate, manifestHasher, propertiesHasher)

    def "uses manifest file hasher for manifest files"() {
        when:
        hasher.hash(zipEntry('META-INF/MANIFEST.MF'))

        then:
        1 * manifestHasher.hash(_)
        0 * propertiesHasher.hash(_)
        0 * delegate.hash(_)
    }

    def "uses property file hasher for properties files"() {
        when:
        hasher.hash(zipEntry('META-INF/build.properties'))

        then:
        1 * propertiesHasher.hash(_)
        0 * manifestHasher.hash(_)
        0 * delegate.hash(_)
    }

    def "uses delegate for META-INF files that are not manifest or meta-inf properties files"() {
        when:
        hasher.hash(zipEntry('META-INF/foo'))
        hasher.hash(zipEntry('META-INF/foo/MANIFEST.MF'))
        hasher.hash(zipEntry('META-INF/properties'))
        hasher.hash(zipEntry('META-INF/build.propertiesX'))
        hasher.hash(zipEntry('bar.properties'))
        hasher.hash(zipEntry('resources/foo.properties'))
        hasher.hash(zipEntry('foo'))
        hasher.hash(zipEntry('org/gradle/foo.class'))
        hasher.hash(zipEntry('MANIFEST.MF'))

        then:
        9 * delegate.hash(_)
        0 * propertiesHasher.hash(_)
        0 * manifestHasher.hash(_)
    }

    def "falls back to delegate when manifest or property file hasher fails"() {
        when:
        hasher.hash(zipEntry('META-INF/MANIFEST.MF'))

        then:
        1 * manifestHasher.hash(_) >> { throw new IOException() }
        1 * delegate.hash(_)
        0 * propertiesHasher.hash(_)

        when:
        hasher.hash(zipEntry('META-INF/build.properties'))

        then:
        1 * propertiesHasher.hash(_) >> { throw new IOException() }
        1 * delegate.hash(_)
        0 * manifestHasher.hash(_)
    }

    def "unexpected failures are thrown"() {
        when:
        hasher.hash(zipEntry('META-INF/MANIFEST.MF'))

        then:
        1 * manifestHasher.hash(_) >> { throw new IllegalArgumentException() }
        0 * delegate.hash(_)
        0 * propertiesHasher.hash(_)

        and:
        thrown(IllegalArgumentException)

        when:
        hasher.hash(zipEntry('META-INF/build.properties'))

        then:
        1 * propertiesHasher.hash(_) >> { throw new IllegalArgumentException() }
        0 * delegate.hash(_)
        0 * manifestHasher.hash(_)

        and:
        thrown(IllegalArgumentException)
    }

    def zipEntry(String path) {
        def zipEntry = new ZipEntry() {
            @Override
            boolean isDirectory() {
                return false
            }

            @Override
            String getName() {
                return path
            }

            @Override
            byte[] getContent() throws IOException {
                return new byte[0]
            }

            @Override
            InputStream getInputStream() {
                return null
            }

            @Override
            int size() {
                return 0
            }
        }
        return new ZipEntryContext(zipEntry, path, "foo.zip")
    }
}
