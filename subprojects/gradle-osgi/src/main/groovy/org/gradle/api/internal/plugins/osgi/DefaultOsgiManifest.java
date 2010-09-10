/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.plugins.osgi;

import aQute.lib.osgi.Analyzer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.plugins.osgi.OsgiManifest;
import org.gradle.util.GUtil;
import org.gradle.util.UncheckedException;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.Manifest;


/**
 * @author Hans Dockter
 */
public class DefaultOsgiManifest extends DefaultManifest implements OsgiManifest {
    private String symbolicName;
    private String name;
    private String version;
    private String description;
    private String license;
    private String vendor;
    private String docURL;

    private File classesDir;

    private Factory<ContainedVersionAnalyzer> analyzerFactory = new DefaultAnalyzerFactory();

    private Map<String, List<String>> instructions = new HashMap<String, List<String>>();

    private FileCollection classpath;

    public DefaultOsgiManifest(FileResolver fileResolver) {
        super(fileResolver);
    }

    @Override
    public DefaultManifest getEffectiveManifest() {
        ContainedVersionAnalyzer analyzer = analyzerFactory.create();
        DefaultManifest effectiveManifest = new DefaultManifest(null);
        try {
            setAnalyzerProperties(analyzer);
            Manifest osgiManifest = analyzer.calcManifest();
            for (Map.Entry<Object, Object> entry : osgiManifest.getMainAttributes().entrySet()) {
                effectiveManifest.attributes(WrapUtil.toMap(entry.getKey().toString(), (String) entry.getValue()));
            }
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
        return getEffectiveManifestInternal(effectiveManifest);
    }

    private void setAnalyzerProperties(Analyzer analyzer) throws IOException {
        for (String instructionName : instructions.keySet()) {
            analyzer.setProperty(instructionName, createPropertyStringFromList(instructionValue(instructionName)));
        }
        setProperty(analyzer, Analyzer.BUNDLE_VERSION, getVersion());
        setProperty(analyzer, Analyzer.BUNDLE_SYMBOLICNAME, getSymbolicName());
        setProperty(analyzer, Analyzer.BUNDLE_NAME, getName());
        setProperty(analyzer, Analyzer.BUNDLE_DESCRIPTION, getDescription());
        setProperty(analyzer, Analyzer.BUNDLE_LICENSE, getLicense());
        setProperty(analyzer, Analyzer.BUNDLE_VENDOR, getVendor());
        setProperty(analyzer, Analyzer.BUNDLE_DOCURL, getDocURL());
        analyzer.setJar(getClassesDir());
        analyzer.setClasspath(getClasspath().getFiles().toArray(new File[getClasspath().getFiles().size()]));
    }

    private void setProperty(Analyzer analyzer, String key, String value) {
        if (value == null) {
            return;
        }
        analyzer.setProperty(key, value);
    }

    public List<String> instructionValue(String instructionName) {
        return instructions.get(instructionName);
    }

    public OsgiManifest instruction(String name, String... values) {
        if (instructions.get(name) == null) {
            instructions.put(name, new ArrayList<String>());
        }
        instructions.get(name).addAll(Arrays.asList(values));
        return this;
    }

    public OsgiManifest instructionFirst(String name, String... values) {
        if (instructions.get(name) == null) {
            instructions.put(name, new ArrayList<String>());
        }
        instructions.get(name).addAll(0, Arrays.asList(values));
        return this;
    }

    public Map<String, List<String>> getInstructions() {
        return instructions;
    }

    public void setInstructions(Map<String, List<String>> instructions) {
        this.instructions = instructions;
    }

    private String createPropertyStringFromList(List<String> valueList) {
        return GUtil.join(valueList, ",");
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public void setSymbolicName(String symbolicName) {
        this.symbolicName = symbolicName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getDocURL() {
        return docURL;
    }

    public void setDocURL(String docURL) {
        this.docURL = docURL;
    }

    public File getClassesDir() {
        return classesDir;
    }

    public void setClassesDir(File classesDir) {
        this.classesDir = classesDir;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    public Factory<ContainedVersionAnalyzer> getAnalyzerFactory() {
        return analyzerFactory;
    }

    public void setAnalyzerFactory(Factory<ContainedVersionAnalyzer> analyzerFactory) {
        this.analyzerFactory = analyzerFactory;
    }
}
