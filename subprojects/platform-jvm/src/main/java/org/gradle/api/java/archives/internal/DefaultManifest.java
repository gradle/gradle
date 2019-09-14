/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.java.archives.internal;

import groovy.lang.Closure;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.ManifestMergeSpec;
import org.gradle.internal.Actions;
import org.gradle.internal.IoActions;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.ClosureBackedAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultManifest implements ManifestInternal {
    public static final String DEFAULT_CONTENT_CHARSET = "UTF-8";

    private List<ManifestMergeSpec> manifestMergeSpecs = new ArrayList<ManifestMergeSpec>();

    private DefaultAttributes attributes = new DefaultAttributes();

    private Map<String, Attributes> sections = new LinkedHashMap<String, Attributes>();

    private PathToFileResolver fileResolver;

    private String contentCharset;

    public DefaultManifest(PathToFileResolver fileResolver) {
        this(fileResolver, DEFAULT_CONTENT_CHARSET);
    }

    public DefaultManifest(PathToFileResolver fileResolver, String contentCharset) {
        this.fileResolver = fileResolver;
        this.contentCharset = contentCharset;
        init();
    }

    public DefaultManifest(Object manifestPath, PathToFileResolver fileResolver) {
        this(manifestPath, fileResolver, DEFAULT_CONTENT_CHARSET);
    }

    public DefaultManifest(Object manifestPath, PathToFileResolver fileResolver, String contentCharset) {
        this.fileResolver = fileResolver;
        this.contentCharset = contentCharset;
        read(manifestPath);
    }

    private void init() {
        getAttributes().put("Manifest-Version", "1.0");
    }

    @Override
    public String getContentCharset() {
        return contentCharset;
    }

    @Override
    public void setContentCharset(String contentCharset) {
        if (contentCharset == null) {
            throw new InvalidUserDataException("contentCharset must not be null");
        }
        if (!Charset.isSupported(contentCharset)) {
            throw new InvalidUserDataException(String.format("Charset for contentCharset '%s' is not supported by your JVM", contentCharset));
        }
        this.contentCharset = contentCharset;
    }

    public DefaultManifest mainAttributes(Map<String, ?> attributes) {
        return attributes(attributes);
    }

    @Override
    public DefaultManifest attributes(Map<String, ?> attributes) {
        getAttributes().putAll(attributes);
        return this;
    }

    @Override
    public DefaultManifest attributes(Map<String, ?> attributes, String sectionName) {
        if (!sections.containsKey(sectionName)) {
            sections.put(sectionName, new DefaultAttributes());
        }
        sections.get(sectionName).putAll(attributes);
        return this;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public Map<String, Attributes> getSections() {
        return sections;
    }

    public DefaultManifest clear() {
        attributes.clear();
        sections.clear();
        manifestMergeSpecs.clear();
        init();
        return this;
    }

    @Override
    public DefaultManifest from(Object... mergePaths) {
        return from(mergePaths, Actions.<ManifestMergeSpec>doNothing());
    }

    @Override
    public DefaultManifest from(Object mergePaths, Closure<?> closure) {
        return from(mergePaths, ClosureBackedAction.<ManifestMergeSpec>of(closure));
    }

    @Override
    public DefaultManifest from(Object mergePath, Action<ManifestMergeSpec> action) {
        DefaultManifestMergeSpec mergeSpec = new DefaultManifestMergeSpec();
        mergeSpec.from(mergePath);
        manifestMergeSpecs.add(mergeSpec);
        action.execute(mergeSpec);
        return this;
    }

    @Override
    public DefaultManifest getEffectiveManifest() {
        return getEffectiveManifestInternal(this);
    }

    protected DefaultManifest getEffectiveManifestInternal(DefaultManifest baseManifest) {
        DefaultManifest resultManifest = baseManifest;
        for (ManifestMergeSpec manifestMergeSpec : manifestMergeSpecs) {
            resultManifest = ((DefaultManifestMergeSpec) manifestMergeSpec).merge(resultManifest, fileResolver);
        }
        return resultManifest;
    }

    @Override
    public org.gradle.api.java.archives.Manifest writeTo(OutputStream outputStream) {
        writeTo(this, outputStream, contentCharset);
        return this;
    }

    static void writeTo(org.gradle.api.java.archives.Manifest manifest, OutputStream outputStream, String contentCharset) {
        org.gradle.api.java.archives.Manifest effectiveManifest = manifest.getEffectiveManifest();
        try {
            ManifestPrinter printer = new ManifestPrinter(new BufferedWriter(new OutputStreamWriter(outputStream, contentCharset)));
            printer.print(effectiveManifest);
            // We don't want to close the output stream since it might belong to a ZipOutputStream
            printer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public org.gradle.api.java.archives.Manifest writeTo(Object path) {
        File manifestFile = fileResolver.resolve(path);
        try {
            File parentFile = manifestFile.getParentFile();
            if (parentFile != null) {
                FileUtils.forceMkdir(parentFile);
            }
            IoActions.withResource(new FileOutputStream(manifestFile), new Action<FileOutputStream>() {
                @Override
                public void execute(FileOutputStream fileOutputStream) {
                    writeTo(fileOutputStream);
                }
            });
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<ManifestMergeSpec> getMergeSpecs() {
        return manifestMergeSpecs;
    }

    public boolean isEqualsTo(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof DefaultManifest)) {
            return false;
        }

        DefaultManifest effectiveThis = getEffectiveManifest();
        DefaultManifest effectiveThat = ((DefaultManifest) o).getEffectiveManifest();

        if (!effectiveThis.attributes.equals(effectiveThat.attributes)) {
            return false;
        }
        if (!effectiveThis.sections.equals(effectiveThat.sections)) {
            return false;
        }

        return true;
    }

    private void read(Object manifestPath) {
        File manifestFile = fileResolver.resolve(manifestPath);
        try (
            ManifestReader mr = new ManifestReader(
                new InputStreamReader(
                    new ManifestInputStream(FileUtils.openInputStream(manifestFile)),
                    contentCharset))
            ) {
            mr.read(this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
