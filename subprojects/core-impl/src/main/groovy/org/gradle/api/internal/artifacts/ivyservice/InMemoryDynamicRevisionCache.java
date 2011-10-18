package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.changedetection.InMemoryIndexedCache;
import org.gradle.cache.PersistentIndexedCache;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.io.Serializable;

class InMemoryDynamicRevisionCache implements DynamicRevisionCache {
    private final PersistentIndexedCache<RevisionKey, String> cache = new InMemoryIndexedCache<RevisionKey, String>();

    public ModuleRevisionId getResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision) {
        String encodedRevision = cache.get(createKey(resolver, dynamicRevision));
        return encodedRevision == null ? null : ModuleRevisionId.decode(encodedRevision);
    }

    public void saveResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision, ModuleRevisionId resolvedRevision) {
        cache.put(createKey(resolver, dynamicRevision), resolvedRevision.encodeToString());
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

}
