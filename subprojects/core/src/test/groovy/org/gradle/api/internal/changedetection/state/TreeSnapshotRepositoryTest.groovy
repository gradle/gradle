/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.internal.cache.StringInterner
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.internal.hash.HashValue
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@UsesNativeServices
class TreeSnapshotRepositoryTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def mapping = Stub(CacheScopeMapping) {
        getBaseDirectory(_, _, _) >> {
            return tmpDir.createDir("history-cache")
        }
    }
    CacheRepository cacheRepository = new DefaultCacheRepository(mapping, new InMemoryCacheFactory())
    TaskArtifactStateCacheAccess cacheAccess = new DefaultTaskArtifactStateCacheAccess(TestUtil.createRootProject().gradle, cacheRepository, new NoOpDecorator())
    @Subject
    TreeSnapshotRepository treeSnapshotRepository = new TreeSnapshotRepository(cacheAccess, new StringInterner())

    def "tree snapshot can be added and retrieved from cache"() {
        given:
        TreeSnapshot treeSnapshot = createTreeSnapshot(1)
        when:
        assert treeSnapshot != null
        treeSnapshotRepository.maybeStoreTreeSnapshot(treeSnapshot)
        then:
        def retrievedSnapshot = treeSnapshotRepository.getTreeSnapshot(1)
        retrievedSnapshot.assignedId == 1
        def fileSnapshots = retrievedSnapshot.fileSnapshots as List
        fileSnapshots.size() == 3
        fileSnapshots.get(0).key == 'a'
        fileSnapshots.get(0).incrementalFileSnapshot instanceof MissingFileSnapshot
        fileSnapshots.get(1).key == 'b'
        fileSnapshots.get(1).incrementalFileSnapshot instanceof DirSnapshot
        fileSnapshots.get(2).key == 'c'
        fileSnapshots.get(2).incrementalFileSnapshot instanceof FileHashSnapshot
        fileSnapshots.get(2).incrementalFileSnapshot.hash.asBigInteger().intValue() == 1
    }

    def "tree snapshot usage is tracked and removed when all dependent file collection snapshots are removed"() {
        given:
        treeSnapshotRepository.maybeStoreTreeSnapshot(createTreeSnapshot(1))
        FileCollectionSnapshot fileCollectionSnapshot = Stub(FileCollectionSnapshot) {
            getTreeSnapshotIds() >> {
                [1L]
            }
        }
        FileCollectionSnapshot fileCollectionSnapshot2 = Stub(FileCollectionSnapshot) {
            getTreeSnapshotIds() >> {
                [1L]
            }
        }
        when:
        treeSnapshotRepository.addTreeSnapshotUsage(fileCollectionSnapshot, 111)
        treeSnapshotRepository.addTreeSnapshotUsage(fileCollectionSnapshot2, 222)
        then:
        treeSnapshotRepository.getTreeSnapshot(1) != null
        when:
        treeSnapshotRepository.removeTreeSnapshotUsage(111)
        then:
        treeSnapshotRepository.getTreeSnapshot(1) != null
        when:
        treeSnapshotRepository.removeTreeSnapshotUsage(222)
        then:
        treeSnapshotRepository.getTreeSnapshot(1) == null
    }

    private TreeSnapshot createTreeSnapshot(final int assignedId) {
        TreeSnapshot treeSnapshot = new TreeSnapshot() {
            boolean isShareable() {
                true
            }

            Collection<FileSnapshotWithKey> getFileSnapshots() {
                [new FileSnapshotWithKey("a", MissingFileSnapshot.instance), new FileSnapshotWithKey("b", DirSnapshot.instance), new FileSnapshotWithKey("c", new FileHashSnapshot(new HashValue("1")))]
            }

            Long getAssignedId() {
                assignedId
            }

            Long maybeStoreEntry(Action<Long> storeEntryAction) {
                storeEntryAction.execute(assignedId)
                assignedId
            }
        }
    }
}
