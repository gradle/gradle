package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.changedetection.InMemoryIndexedCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.TimeProvider;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.io.Serializable;

class InMemoryDynamicRevisionCache implements DynamicRevisionCache {
    private static final PersistentIndexedCache<RevisionKey, CachedRevision> CACHE = new InMemoryIndexedCache<RevisionKey, CachedRevision>();
    private final TimeProvider timeProvider;

    public InMemoryDynamicRevisionCache(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public CachedRevision getResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision) {
        return CACHE.get(createKey(resolver, dynamicRevision));
    }

    public void saveResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision, ModuleRevisionId resolvedRevision) {
        CACHE.put(createKey(resolver, dynamicRevision), new DefaultCachedRevision(resolvedRevision, timeProvider.getCurrentTime()));
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
    
    private static class DefaultCachedRevision implements CachedRevision, Serializable {
        private final String encodedRevisionId;
        private final long createTimestamp;

        private DefaultCachedRevision(ModuleRevisionId revisionId, long createTimestamp) {
            this.createTimestamp = createTimestamp;
            this.encodedRevisionId = revisionId.encodeToString();
        }

        public ModuleRevisionId getRevision() {
            return ModuleRevisionId.decode(encodedRevisionId);
        }

        public long getCreateTimestamp() {
            return createTimestamp;
        }
    }
}
