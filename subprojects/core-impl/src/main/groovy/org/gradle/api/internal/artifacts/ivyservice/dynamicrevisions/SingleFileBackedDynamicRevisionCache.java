package org.gradle.api.internal.artifacts.ivyservice.dynamicrevisions;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.ReusableFileLock;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.util.TimeProvider;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.io.File;
import java.io.Serializable;

public class SingleFileBackedDynamicRevisionCache implements DynamicRevisionCache {
    // TODO I think this would be better with a file per resolver (or even a file per dynamic-revision per resolver)
    // Locking would be simpler, if nothing else.
    private final PersistentIndexedCache<RevisionKey, CachedRevisionEntry> cache;
    private final TimeProvider timeProvider;
    private final ReusableFileLock dynamicRevisionsLock;

    public SingleFileBackedDynamicRevisionCache(TimeProvider timeProvider, File cacheBaseDir, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        File dynamicRevisionsFile = new File(cacheBaseDir, "dynamic-revisions.bin");
        dynamicRevisionsLock = (ReusableFileLock) cacheLockingManager.getCacheMetadataLock(dynamicRevisionsFile);
        cache = initCache(dynamicRevisionsFile);
    }

    private PersistentIndexedCache<RevisionKey, CachedRevisionEntry> initCache(File dynamicRevisionsFile) {
        dynamicRevisionsLock.lock();
        try {
            return new BTreePersistentIndexedCache<RevisionKey, CachedRevisionEntry>(dynamicRevisionsFile, dynamicRevisionsLock,
                    new DefaultSerializer<CachedRevisionEntry>(CachedRevisionEntry.class.getClassLoader()));
        } finally {
            dynamicRevisionsLock.unlock();
        }
    }

    public CachedRevision getResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision) {
        dynamicRevisionsLock.lock();
        try {
            CachedRevisionEntry cachedRevisionEntry = cache.get(createKey(resolver, dynamicRevision));
            return cachedRevisionEntry == null ? null : new DefaultCachedRevision(cachedRevisionEntry, timeProvider);
        } finally {
            dynamicRevisionsLock.unlock();
        }
    }

    public void saveResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision, ModuleRevisionId resolvedRevision) {
        dynamicRevisionsLock.lock();
        try {
            cache.put(createKey(resolver, dynamicRevision), createEntry(resolvedRevision));
        } finally {
            dynamicRevisionsLock.unlock();
        }
    }

    private RevisionKey createKey(DependencyResolver resolver, ModuleRevisionId revisionId) {
        return new RevisionKey(resolver, revisionId);
    }

    private static class RevisionKey implements Serializable {
         private final String resolverId;
         private final String revisionId;

         private RevisionKey(DependencyResolver resolver, ModuleRevisionId revision) {
             this.resolverId = new WharfResolverMetadata(resolver).getId();
             this.revisionId = revision.encodeToString();
         }

         @Override
         public boolean equals(Object o) {
             if (o == null || !(o instanceof RevisionKey)) {
                 return false;
             }
             RevisionKey other = (RevisionKey) o;
             return resolverId.equals(other.resolverId) && revisionId.equals(other.revisionId);
         }

         @Override
         public int hashCode() {
             return resolverId.hashCode() ^ revisionId.hashCode();
         }
     }

    private CachedRevisionEntry createEntry(ModuleRevisionId revisionId) {
        return new CachedRevisionEntry(revisionId, timeProvider);
    }

}
