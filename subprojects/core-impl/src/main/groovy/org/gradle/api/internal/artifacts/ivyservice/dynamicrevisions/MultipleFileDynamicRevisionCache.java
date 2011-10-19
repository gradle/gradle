package org.gradle.api.internal.artifacts.ivyservice.dynamicrevisions;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentStateCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.OnDemandFileLock;
import org.gradle.cache.internal.SimpleStateCache;
import org.gradle.util.TimeProvider;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.io.File;

public class MultipleFileDynamicRevisionCache implements DynamicRevisionCache {
    
    private final TimeProvider timeProvider;
    private final File cacheBaseDir;
    private final FileLockManager fileLockManager;

    public MultipleFileDynamicRevisionCache(TimeProvider timeProvider, File cacheBaseDir, FileLockManager fileLockManager) {
        this.timeProvider = timeProvider;
        this.cacheBaseDir = cacheBaseDir;
        this.fileLockManager = fileLockManager;
    }

    public CachedRevision getResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision) {
        CachedRevisionEntry cachedRevisionEntry = getCache(resolver, dynamicRevision).get();
        return cachedRevisionEntry == null ? null : new DefaultCachedRevision(cachedRevisionEntry, timeProvider);
    }

    public void saveResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision, ModuleRevisionId resolvedRevision) {
        CachedRevisionEntry entry = createEntry(resolvedRevision);
        getCache(resolver, dynamicRevision).set(entry);
    }

    private CachedRevisionEntry createEntry(ModuleRevisionId revisionId) {
        return new CachedRevisionEntry(revisionId, timeProvider);
    }
    
    private PersistentStateCache<CachedRevisionEntry> getCache(DependencyResolver resolver, ModuleRevisionId dynamicRevision) {
        File cacheFile = getCacheFile(resolver, dynamicRevision);
        return new SimpleStateCache<CachedRevisionEntry>(cacheFile, new OnDemandFileLock(cacheFile, "dynamic revisions cache", fileLockManager),
                new DefaultSerializer<CachedRevisionEntry>(CachedRevisionEntry.class.getClassLoader()));
    }
    
    private File getCacheFile(DependencyResolver resolver, ModuleRevisionId dynamicRevision) {
        String cacheFilePath = IvyPatternHelper.substitute(getDataFilePattern(resolver), dynamicRevision);
        return new File(cacheBaseDir, cacheFilePath);
    }

    public String getDataFilePattern(DependencyResolver resolver) {
        String resolverId = new WharfResolverMetadata(resolver).getId();
        return String.format("[organisation]/[module](/[branch])/%s/resolved-[revision].bin", resolverId);
    }

}
