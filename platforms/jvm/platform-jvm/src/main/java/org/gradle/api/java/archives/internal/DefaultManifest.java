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
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.ManifestMergeSpec;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.internal.ClosureBackedAction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

public class DefaultManifest implements ManifestInternal {

    private final List<ManifestMergeSpec> manifestMergeSpecs = new ArrayList<ManifestMergeSpec>();
    private final DefaultAttributes attributes = new DefaultAttributes();
    private final Map<String, Attributes> sections = new LinkedHashMap<String, Attributes>();
    private final PathToFileResolver fileResolver;
    private final ProviderFactory providerFactory;
    private final ManifestFactory manifestFactory;
    private final Provider<String> contentCharset;

    public DefaultManifest(PathToFileResolver fileResolver, ProviderFactory providerFactory, ManifestFactory manifestFactory, Provider<String> contentCharset) {
        this.fileResolver = fileResolver;
        this.providerFactory = providerFactory;
        this.manifestFactory = manifestFactory;
        this.contentCharset = contentCharset.orElse(DEFAULT_CONTENT_CHARSET);
    }

    public DefaultManifest init() {
        getAttributes().put("Manifest-Version", "1.0");
        return this;
    }

    public Provider<String> getContentCharset() {
        return contentCharset;
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
        DefaultManifestMergeSpec mergeSpec = new DefaultManifestMergeSpec(manifestFactory, providerFactory);
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
            resultManifest = ((DefaultManifestMergeSpec) manifestMergeSpec).merge(resultManifest);
        }
        return resultManifest;
    }

    @Override
    public org.gradle.api.java.archives.Manifest writeTo(OutputStream outputStream) {
        ManifestWriter.writeTo(this, outputStream, contentCharset.get());
        return this;
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
                    ManifestWriter.writeTo(DefaultManifest.this, fileOutputStream, contentCharset.get());
                }
            });
            return this;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public List<ManifestMergeSpec> getMergeSpecs() {
        return manifestMergeSpecs;
    }

    @SuppressWarnings("StringCharset") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public DefaultManifest read(Object manifestPath) {
        File manifestFile = fileResolver.resolve(manifestPath);
        try {
            byte[] manifestBytes = FileUtils.readFileToByteArray(manifestFile);
            manifestBytes = prepareManifestBytesForInteroperability(manifestBytes);
            // Eventually convert manifest content to UTF-8 before handing it to java.util.jar.Manifest
            if (!DEFAULT_CONTENT_CHARSET.equals(contentCharset.get())) {
                manifestBytes = new String(manifestBytes, contentCharset.get()).getBytes(DEFAULT_CONTENT_CHARSET);
            }
            // Effectively read the manifest
            Manifest javaManifest = new Manifest(new ByteArrayInputStream(manifestBytes));
            addJavaManifestToAttributes(javaManifest);
            addJavaManifestToSections(javaManifest);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return this;
    }

    /**
     * Prepare Manifest bytes for interoperability. Ant Manifest class doesn't support split multi-bytes characters, Java Manifest class does. Ant Manifest class supports manifest sections starting
     * without prior blank lines, Java Manifest class doesn't. Ant Manifest class supports manifest without last line blank, Java Manifest class doesn't. Therefore we need to insert blank lines before
     * entries named 'Name' and before EOF if needed. This without decoding characters as this would break split multi-bytes characters, hence working on the bytes level.
     */
    private byte[] prepareManifestBytesForInteroperability(byte[] original) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean useCarriageReturns = false;
        byte carriageReturn = (byte) '\r';
        byte newLine = (byte) '\n';
        for (int idx = 0; idx < original.length; idx++) {
            byte current = original[idx];
            if (current == carriageReturn) {
                useCarriageReturns = true;
            }
            if (idx == original.length - 1) {
                // Always append a new line at EOF
                output.write(current);
                if (useCarriageReturns) {
                    output.write(carriageReturn);
                }
                output.write(newLine);
            } else if (current == newLine && idx + 5 < original.length) {
                // Eventually add blank line before section
                output.write(current);
                if ((original[idx + 1] == 'N' || original[idx + 1] == 'n')
                    && (original[idx + 2] == 'A' || original[idx + 2] == 'a')
                    && (original[idx + 3] == 'M' || original[idx + 3] == 'm')
                    && (original[idx + 4] == 'E' || original[idx + 4] == 'e')
                    && (original[idx + 5] == ':')) {
                    if (useCarriageReturns) {
                        output.write(carriageReturn);
                    }
                    output.write(newLine);
                }
            } else {
                output.write(current);
            }
        }
        return output.toByteArray();
    }

    private void addJavaManifestToAttributes(Manifest javaManifest) {
        attributes.put("Manifest-Version", "1.0");
        for (Object attributeKey : javaManifest.getMainAttributes().keySet()) {
            String attributeName = attributeKey.toString();
            String attributeValue = javaManifest.getMainAttributes().getValue(attributeName);
            attributes.put(attributeName, attributeValue);
        }
    }

    private void addJavaManifestToSections(Manifest javaManifest) {
        for (Map.Entry<String, java.util.jar.Attributes> sectionEntry : javaManifest.getEntries().entrySet()) {
            String sectionName = sectionEntry.getKey();
            DefaultAttributes sectionAttributes = new DefaultAttributes();
            for (Object attributeKey : sectionEntry.getValue().keySet()) {
                String attributeName = attributeKey.toString();
                String attributeValue = sectionEntry.getValue().getValue(attributeName);
                sectionAttributes.put(attributeName, attributeValue);
            }
            sections.put(sectionName, sectionAttributes);
        }
    }
}
