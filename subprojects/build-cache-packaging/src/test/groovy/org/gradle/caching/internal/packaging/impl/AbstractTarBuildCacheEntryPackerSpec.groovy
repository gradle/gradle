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

package org.gradle.caching.internal.packaging.impl

import groovy.transform.Immutable
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.origin.OriginReader
import org.gradle.caching.internal.origin.OriginWriter
import org.gradle.internal.MutableReference
import org.gradle.internal.file.Deleter
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint
import org.gradle.internal.hash.DefaultStreamHasher
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.file.TreeType.DIRECTORY
import static org.gradle.internal.file.TreeType.FILE

@CleanupTestDirectory
abstract class AbstractTarBuildCacheEntryPackerSpec extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def readOrigin = Stub(OriginReader)
    def writeOrigin = Stub(OriginWriter)

    def filePermissionAccess = createFilePermissionAccess()
    def deleter = createDeleter()
    def fileSystemSupport = new DefaultTarPackerFileSystemSupport(deleter)
    def streamHasher = new DefaultStreamHasher()
    def stringInterner = new StringInterner()
    def packer = new TarBuildCacheEntryPacker(fileSystemSupport, filePermissionAccess, streamHasher, stringInterner)
    def virtualFileSystem = TestFiles.virtualFileSystem()

    abstract protected FilePermissionAccess createFilePermissionAccess()
    abstract protected Deleter createDeleter()

    def pack(OutputStream output, OriginWriter writeOrigin = this.writeOrigin, TreeDefinition... treeDefs) {
        Map<String, CurrentFileCollectionFingerprint> fingerprints = treeDefs.collectEntries { treeDef ->
            return [(treeDef.tree.name): treeDef.fingerprint()]
        }
        packer.pack(entity(treeDefs), fingerprints, output, writeOrigin)
    }

    def unpack(InputStream input, OriginReader readOrigin = this.readOrigin, TreeDefinition... treeDefs) {
        packer.unpack(entity(treeDefs), input, readOrigin)
    }

    def entity(TreeDefinition... treeDefs) {
        Stub(CacheableEntity) {
            visitOutputTrees(_ as CacheableEntity.CacheableTreeVisitor) >> { CacheableEntity.CacheableTreeVisitor visitor ->
                treeDefs.each {
                    if (it.tree.root != null) {
                        visitor.visitOutputTree(it.tree.name, it.tree.type, it.tree.root)
                    }
                }
            }
        }
    }

    def prop(String name = "test", TreeType type, File output, FingerprintingStrategy fingerprintingStrategy = AbsolutePathFingerprintingStrategy.IGNORE_MISSING) {
        switch (type) {
            case FILE:
                return new TreeDefinition(new TestCacheableTree(name, FILE, output)) {
                    @Override
                    CurrentFileCollectionFingerprint fingerprint() {
                        if (output == null) {
                            return fingerprintingStrategy.getEmptyFingerprint()
                        }
                        return fingerprint(output, fingerprintingStrategy)
                    }
                }
            case DIRECTORY:
                return new TreeDefinition(new TestCacheableTree(name, DIRECTORY, output)) {
                    @Override
                    CurrentFileCollectionFingerprint fingerprint() {
                        if (output == null) {
                            return fingerprintingStrategy.getEmptyFingerprint()
                        }
                        return fingerprint(output, fingerprintingStrategy)
                    }
                }
            default:
                throw new AssertionError()
        }
    }

    protected abstract static class TreeDefinition {
        final TestCacheableTree tree

        TreeDefinition(TestCacheableTree tree) {
            this.tree = tree
        }

        abstract CurrentFileCollectionFingerprint fingerprint()
    }

    protected CurrentFileCollectionFingerprint fingerprint(File file, FingerprintingStrategy strategy) {
        MutableReference<CurrentFileCollectionFingerprint> fingerprint = MutableReference.empty()
        virtualFileSystem.read(file.getAbsolutePath()) { snapshot ->
            fingerprint.set(DefaultCurrentFileCollectionFingerprint.from([snapshot], strategy))
        }
        return fingerprint.get()
    }

    @Immutable(knownImmutableClasses = [File])
    static class TestCacheableTree {
        String name
        TreeType type
        File root
    }
}
