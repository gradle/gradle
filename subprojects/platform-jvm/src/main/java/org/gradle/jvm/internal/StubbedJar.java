/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.jvm.internal;

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.archive.ZipCopyAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.UncheckedException;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.internal.tasks.apigen.ApiStubGenerator;

import java.io.*;
import java.util.Collection;
import java.util.Map;

public class StubbedJar extends Jar {

    private Collection<String> exportedPackages;

    @Input
    public Collection<String> getExportedPackages() {
        return exportedPackages;
    }

    public void setExportedPackages(Collection<String> exportedPackages) {
        this.exportedPackages = exportedPackages;
    }

    @Override
    protected CopyAction createCopyAction() {
        final ZipCopyAction zipAction = (ZipCopyAction) super.createCopyAction();
        ApiStubGenerator stubGenerator = new ApiStubGenerator(ImmutableList.copyOf(getExportedPackages()));
        return new StubCopyActionDecorator(zipAction, stubGenerator);
    }

    private static class StubCopyActionDecorator implements CopyAction {

        private final CopyAction delegate;
        private final ApiStubGenerator stubGenerator;

        public StubCopyActionDecorator(CopyAction delegate, ApiStubGenerator stubGenerator) {
            this.delegate = delegate;
            this.stubGenerator = stubGenerator;
        }

        public WorkResult execute(final CopyActionProcessingStream stream) {
            return delegate.execute(new CopyActionProcessingStream() {
                public void process(final CopyActionProcessingStreamAction action) {
                    stream.process(new CopyActionProcessingStreamAction() {
                        public void processFile(FileCopyDetailsInternal details) {
                            action.processFile(new StubFileDetailsDecorator(details, stubGenerator));
                        }
                    });
                }
            });
        }
    }

    private static class StubFileDetailsDecorator implements FileCopyDetailsInternal {
        private final FileCopyDetailsInternal delegate;
        private final ApiStubGenerator stubGenerator;

        private StubFileDetailsDecorator(FileCopyDetailsInternal delegate, ApiStubGenerator stubGenerator) {
            this.delegate = delegate;
            this.stubGenerator = stubGenerator;
        }

        @Override
        public boolean isIncludeEmptyDirs() {
            return delegate.isIncludeEmptyDirs();
        }

        @Override
        public void exclude() {
            delegate.exclude();
        }

        @Override
        @Incubating
        public DuplicatesStrategy getDuplicatesStrategy() {
            return delegate.getDuplicatesStrategy();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getPath() {
            return delegate.getPath();
        }

        @Override
        public RelativePath getRelativePath() {
            return delegate.getRelativePath();
        }

        @Override
        public RelativePath getRelativeSourcePath() {
            return delegate.getRelativeSourcePath();
        }

        @Override
        public String getSourceName() {
            return delegate.getSourceName();
        }

        @Override
        public String getSourcePath() {
            return delegate.getSourcePath();
        }

        @Override
        @Incubating
        public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
            delegate.setDuplicatesStrategy(strategy);
        }

        @Override
        public void setMode(int mode) {
            delegate.setMode(mode);
        }

        @Override
        public void setName(String name) {
            delegate.setName(name);
        }

        @Override
        public void setPath(String path) {
            delegate.setPath(path);
        }

        @Override
        public void setRelativePath(RelativePath path) {
            delegate.setRelativePath(path);
        }

        @Override
        public void copyTo(OutputStream outstr) {
            if (delegate.getName().endsWith(".class")) {
                InputStream delegateStream = open();
                try {
                    outstr.write(stubGenerator.convertToApi(delegateStream));
                    return;
                } catch (IOException e) {
                    delegate.copyTo(outstr);
                } finally {
                    try {
                        delegateStream.close();
                    } catch (IOException e) {
                        UncheckedException.throwAsUncheckedException(e);
                    }
                }
            }
            delegate.copyTo(outstr);
        }

        @Override
        public boolean copyTo(File target) {
            return delegate.copyTo(target);
        }

        @Override
        public File getFile() {
            return delegate.getFile();
        }

        @Override
        public long getLastModified() {
            // we need all ABI jars to be the same independently of when they were built
            // so the timestamp has to be identical
            return 0;
        }

        @Override
        public int getMode() {
            return delegate.getMode();
        }

        @Override
        public long getSize() {
            return delegate.getSize();
        }

        @Override
        public boolean isDirectory() {
            return delegate.isDirectory();
        }

        @Override
        public InputStream open() {
            return delegate.open();
        }

        @Override
        public ContentFilterable expand(Map<String, ?> properties) {
            return delegate.expand(properties);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public ContentFilterable filter(Closure closure) {
            return delegate.filter(closure);
        }

        @Override
        public ContentFilterable filter(Class<? extends FilterReader> filterType) {
            return delegate.filter(filterType);
        }

        @Override
        public ContentFilterable filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
            return delegate.filter(properties, filterType);
        }
    }
}
