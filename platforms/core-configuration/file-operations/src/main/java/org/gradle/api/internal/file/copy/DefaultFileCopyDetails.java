/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.file.copy;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFilePermissions;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.CopyProcessingSpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ExpandDetails;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.internal.file.DefaultConfigurableFilePermissions;
import org.gradle.api.internal.file.DefaultExpandDetails;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Actions;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class DefaultFileCopyDetails extends AbstractFileTreeElement implements FileVisitDetails, FileCopyDetailsInternal {
    private final FileVisitDetails fileDetails;
    private final CopySpecResolver specResolver;
    private final Instantiator instantiator;
    private final FilterChain filterChain;
    private final PropertyFactory propertyFactory;
    private boolean defaultDuplicatesStrategy;
    private RelativePath relativePath;
    private boolean excluded;

    private DefaultConfigurableFilePermissions permissions;
    private DuplicatesStrategy duplicatesStrategy;

    @Inject
    public DefaultFileCopyDetails(FileVisitDetails fileDetails, CopySpecResolver specResolver, Instantiator instantiator, PropertyFactory propertyFactory, Chmod chmod) {
        super(chmod);
        this.filterChain = new FilterChain(specResolver.getFilteringCharset());
        this.fileDetails = fileDetails;
        this.specResolver = specResolver;
        this.instantiator = instantiator;
        this.propertyFactory = propertyFactory;
        this.duplicatesStrategy = specResolver.getDuplicatesStrategy();
        this.defaultDuplicatesStrategy = specResolver.isDefaultDuplicateStrategy();
    }

    @Override
    public boolean isIncludeEmptyDirs() {
        return specResolver.getIncludeEmptyDirs();
    }

    @Override
    public String getDisplayName() {
        return fileDetails.toString();
    }

    @Override
    public void stopVisiting() {
        fileDetails.stopVisiting();
    }

    @Override
    public File getFile() {
        if (filterChain.hasFilters()) {
            throw new UnsupportedOperationException();
        } else {
            return fileDetails.getFile();
        }
    }

    @Override
    public boolean isDirectory() {
        return fileDetails.isDirectory();
    }

    @Override
    public long getLastModified() {
        return fileDetails.getLastModified();
    }

    @Override
    public long getSize() {
        if (filterChain.hasFilters()) {
            ByteCountingOutputStream outputStream = new ByteCountingOutputStream();
            copyTo(outputStream);
            return outputStream.size;
        } else {
            return fileDetails.getSize();
        }
    }

    @Override
    public InputStream open() {
        if (filterChain.hasFilters()) {
            return filterChain.transform(fileDetails.open());
        } else {
            return fileDetails.open();
        }
    }

    @Override
    public void copyTo(OutputStream output) {
        if (filterChain.hasFilters()) {
            super.copyTo(output);
        } else {
            fileDetails.copyTo(output);
        }
    }

    @Override
    public boolean copyTo(File target) {
        if (filterChain.hasFilters()) {
            return super.copyTo(target);
        } else {
            final boolean copied = fileDetails.copyTo(target);
            adaptPermissions(target);
            return copied;
        }
    }

    private void adaptPermissions(File target) {
        int specMode = this.getPermissions().toUnixNumeric();
        getChmod().chmod(target, specMode);
    }

    @Override
    public RelativePath getRelativePath() {
        if (relativePath == null) {
            RelativePath path = fileDetails.getRelativePath();
            relativePath = specResolver.getDestPath().append(path.isFile(), path.getSegments());
        }
        return relativePath;
    }

    @Override
    public FilePermissions getPermissions() {
        if (permissions != null) {
            return permissions;
        }

        Provider<FilePermissions> specMode = getSpecMode();
        if (specMode.isPresent()) {
            return specMode.get();
        }

        return fileDetails.getPermissions();
    }

    private Provider<FilePermissions> getSpecMode() {
        return fileDetails.isDirectory() ? specResolver.getImmutableDirPermissions() : specResolver.getImmutableFilePermissions();
    }

    @Override
    public void setRelativePath(RelativePath path) {
        this.relativePath = path;
    }

    @Override
    public void setName(String name) {
        relativePath = getRelativePath().replaceLastName(name);
    }

    @Override
    public void setPath(String path) {
        relativePath = RelativePath.parse(getRelativePath().isFile(), path);
    }

    boolean isExcluded() {
        return excluded;
    }

    @Override
    public void exclude() {
        excluded = true;
    }

    @Override
    @Deprecated
    public void setMode(int mode) {
        DeprecationLogger.deprecateMethod(CopyProcessingSpec.class, "setMode()")
            .replaceWith("permissions(Action)")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "unix_file_permissions_deprecated")
            .nagUser();
        getPermissionsHolder().unix(mode);
    }

    @Override
    public void permissions(Action<? super ConfigurableFilePermissions> configureAction) {
        configureAction.execute(getPermissionsHolder());
    }

    @Override
    public void setPermissions(FilePermissions permissions) {
        getPermissionsHolder().unix(permissions.toUnixNumeric());
    }

    private DefaultConfigurableFilePermissions getPermissionsHolder() {
        if (permissions == null) {
            permissions = instantiator.newInstance(DefaultConfigurableFilePermissions.class, DefaultConfigurableFilePermissions.getDefaultUnixNumeric(fileDetails.isDirectory()));
        }
        return permissions;
    }

    @Override
    public ContentFilterable filter(Closure closure) {
        return filter(new ClosureBackedTransformer(closure));
    }

    @Override
    public ContentFilterable filter(Transformer<String, String> transformer) {
        filterChain.add(transformer);
        return this;
    }

    @Override
    public ContentFilterable filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        filterChain.add(filterType, properties);
        return this;
    }

    @Override
    public ContentFilterable filter(Class<? extends FilterReader> filterType) {
        filterChain.add(filterType);
        return this;
    }

    @Override
    public ContentFilterable expand(Map<String, ?> properties) {
        return expand(properties, Actions.doNothing());
    }

    @Override
    public ContentFilterable expand(Map<String, ?> properties, Action<? super ExpandDetails> action) {
        ExpandDetails details = instantiator.newInstance(DefaultExpandDetails.class, propertyFactory);
        action.execute(details);
        filterChain.expand(properties, details.getEscapeBackslash());
        return this;
    }

    @Override
    public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
        this.duplicatesStrategy = strategy;
        this.defaultDuplicatesStrategy = strategy == DuplicatesStrategy.INHERIT;
    }

    @Override
    public DuplicatesStrategy getDuplicatesStrategy() {
        return this.duplicatesStrategy;
    }

    @Override
    public boolean isDefaultDuplicatesStrategy() {
        return defaultDuplicatesStrategy;
    }

    @Override
    public String getSourceName() {
        return this.fileDetails.getName();
    }

    @Override
    public String getSourcePath() {
        return this.fileDetails.getPath();
    }

    @Override
    public RelativePath getRelativeSourcePath() {
        return this.fileDetails.getRelativePath();
    }

    private static class ByteCountingOutputStream extends OutputStream {
        long size;

        @Override
        public void write(int b) throws IOException {
            size++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            size += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            size += len;
        }
    }
}
