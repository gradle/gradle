package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.internal.artifacts.ivyservice.LocalFileRepositoryCacheManager;

public class LocalFileSystemResolver extends FileSystemResolver {

    private static final String FILE_URL_PREFIX = "file:";

    public LocalFileSystemResolver(String name) {
        setRepositoryCacheManager(new LocalFileRepositoryCacheManager(name));
    }

    @Override
    public void addArtifactPattern(String pattern) {
        super.addArtifactPattern(getFilePath(pattern));
    }

    @Override
    public void addIvyPattern(String pattern) {
        super.addIvyPattern(getFilePath(pattern));
    }

    private String getFilePath(String pattern) {
        if (!pattern.startsWith(FILE_URL_PREFIX)) {
            throw new IllegalArgumentException("Can only handle URIs of type file");
        }
        return pattern.substring(FILE_URL_PREFIX.length());
    }
}
