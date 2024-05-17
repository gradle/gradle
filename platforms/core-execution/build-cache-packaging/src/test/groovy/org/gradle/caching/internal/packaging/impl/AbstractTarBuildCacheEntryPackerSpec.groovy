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
import org.gradle.internal.file.BufferProvider
import org.gradle.internal.file.Deleter
import org.gradle.internal.file.TreeType
import org.gradle.internal.hash.DefaultStreamHasher
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

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
    def packer = new TarBuildCacheEntryPacker(fileSystemSupport, filePermissionAccess, streamHasher, stringInterner, Stub(BufferProvider) {
        getBuffer() >> new byte[4096]
    })
    def fileSystemAccess = TestFiles.fileSystemAccess()

    abstract protected FilePermissionAccess createFilePermissionAccess()
    abstract protected Deleter createDeleter()

    def pack(OutputStream output, OriginWriter writeOrigin = this.writeOrigin, TreeDefinition... treeDefs) {
        Map<String, FileSystemSnapshot> snapshots = treeDefs.collectEntries { treeDef ->
            FileSystemSnapshot result = FileSystemSnapshot.EMPTY
            if (treeDef.root != null) {
                result = fileSystemAccess.read(treeDef.root.absolutePath)
            }
            return [(treeDef.name): result]
        }
        packer.pack(entity(treeDefs), snapshots, output, writeOrigin)
    }

    def unpack(InputStream input, OriginReader readOrigin = this.readOrigin, TreeDefinition... treeDefs) {
        packer.unpack(entity(treeDefs), input, readOrigin)
    }

    def entity(TreeDefinition... treeDefs) {
        Stub(CacheableEntity) {
            visitOutputTrees(_ as CacheableEntity.CacheableTreeVisitor) >> { CacheableEntity.CacheableTreeVisitor visitor ->
                treeDefs.each {
                    if (it.root != null) {
                        visitor.visitOutputTree(it.name, it.type, it.root)
                    }
                }
            }
        }
    }

    def prop(String name = "test", TreeType type, File output) {
        return new TreeDefinition(name, type, output)
    }

    @Immutable(knownImmutableClasses = [File])
    private static class TreeDefinition {
        String name
        TreeType type
        File root
    }
}
