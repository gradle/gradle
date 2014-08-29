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
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.Manifest.Attribute;
import org.apache.tools.ant.taskdefs.Manifest.Section;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.ManifestMergeSpec;
import org.gradle.util.ConfigureUtil;

import java.io.*;
import java.util.*;

public class DefaultManifest implements org.gradle.api.java.archives.Manifest {
    private List<ManifestMergeSpec> manifestMergeSpecs = new ArrayList<ManifestMergeSpec>();

    private DefaultAttributes attributes = new DefaultAttributes();

    private Map<String, Attributes> sections = new LinkedHashMap<String, Attributes>();

    private FileResolver fileResolver;

    public DefaultManifest(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
        init();
    }

    public DefaultManifest(Object manifestPath, FileResolver fileResolver) {
        this.fileResolver = fileResolver;
        read(manifestPath);
    }

    private void init() {
        getAttributes().put("Manifest-Version", "1.0");
    }

    public DefaultManifest mainAttributes(Map<String, ?> attributes) {
        return attributes(attributes);
    }

    public DefaultManifest attributes(Map<String, ?> attributes) {
        getAttributes().putAll(attributes);
        return this;
    }

    public DefaultManifest attributes(Map<String, ?> attributes, String sectionName) {
        if (!sections.containsKey(sectionName)) {
            sections.put(sectionName, new DefaultAttributes());
        }
        sections.get(sectionName).putAll(attributes);
        return this;
    }

    public Attributes getAttributes() {
        return attributes;
    }

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

    private Manifest generateAntManifest() {
        Manifest antManifest = new Manifest();
        addAttributesToAnt(antManifest);
        addSectionAttributesToAnt(antManifest);
        return antManifest;
    }

    private void addAttributesToAnt(Manifest antManifest) {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            try {
                antManifest.addConfiguredAttribute(new Attribute(entry.getKey(), entry.getValue().toString()));
            } catch (ManifestException e) {
                throw new org.gradle.api.java.archives.ManifestException(e.getMessage(), e);
            }
        }
    }

    private void addSectionAttributesToAnt(Manifest antManifest) {
        for (Map.Entry<String, Attributes> entry : sections.entrySet()) {
            Section section = new Section();
            section.setName(entry.getKey());
            try {
                antManifest.addConfiguredSection(section);
                for (Map.Entry<String, Object> attributeEntry : entry.getValue().entrySet()) {
                    section.addConfiguredAttribute(new Attribute(attributeEntry.getKey(), attributeEntry.getValue().toString()));
                }
            } catch (ManifestException e) {
                throw new org.gradle.api.java.archives.ManifestException(e.getMessage(), e);
            }
        }
    }

    public DefaultManifest from(Object... mergePaths) {
        from(mergePaths, null);
        return this;
    }

    public DefaultManifest from(Object mergePaths, Closure<?> closure) {
        DefaultManifestMergeSpec mergeSpec = new DefaultManifestMergeSpec();
        mergeSpec.from(mergePaths);
        manifestMergeSpecs.add(mergeSpec);
        ConfigureUtil.configure(closure, mergeSpec);
        return this;
    }

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

    public DefaultManifest writeTo(Writer writer) {
        PrintWriter printWriter = new PrintWriter(writer);
        try {
            getEffectiveManifest().generateAntManifest().write(printWriter);
            printWriter.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public org.gradle.api.java.archives.Manifest writeTo(Object path) {
        IoActions.writeTextFile(fileResolver.resolve(path), new ErroringAction<Writer>() {
            @Override
            protected void doExecute(Writer writer) throws Exception {
                writeTo(writer);
            }
        });
        return this;
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
        try {
            FileReader reader = new FileReader(manifestFile);
            Manifest antManifest;
            try {
                antManifest = new Manifest(reader);
            } finally {
                reader.close();
            }
            addAntManifestToAttributes(antManifest);
            addAntManifestToSections(antManifest);
        } catch (ManifestException e) {
            throw new org.gradle.api.java.archives.ManifestException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addAntManifestToAttributes(Manifest antManifest) {
        Enumeration<String> attributeKeys = antManifest.getMainSection().getAttributeKeys();
        while (attributeKeys.hasMoreElements()) {
            String key = attributeKeys.nextElement();
            String attributeKey = antManifest.getMainSection().getAttribute(key).getName();
            attributes.put(attributeKey, antManifest.getMainSection().getAttributeValue(key));
        }
        attributes.put("Manifest-Version", antManifest.getManifestVersion());
    }

    private void addAntManifestToSections(Manifest antManifest) {
        Enumeration<String> sectionNames = antManifest.getSectionNames();
        while (sectionNames.hasMoreElements()) {
            String sectionName = sectionNames.nextElement();
            addAntManifestToSection(antManifest, sectionName);
        }
    }

    private void addAntManifestToSection(Manifest antManifest, String sectionName) {
        DefaultAttributes attributes = new DefaultAttributes();
        sections.put(sectionName, attributes);
        Enumeration<String> attributeKeys = antManifest.getSection(sectionName).getAttributeKeys();
        while (attributeKeys.hasMoreElements()) {
            String key = attributeKeys.nextElement();
            String attributeKey = antManifest.getSection(sectionName).getAttribute(key).getName();
            attributes.put(attributeKey, antManifest.getSection(sectionName).getAttributeValue(key));
        }
    }
}