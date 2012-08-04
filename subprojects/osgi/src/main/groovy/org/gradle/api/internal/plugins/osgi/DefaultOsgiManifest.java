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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.plugins.osgi.OsgiManifest;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.Manifest;

/**
 * @author Hans Dockter
 */
public class DefaultOsgiManifest extends DefaultManifest implements OsgiManifest {
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
            java.util.jar.Attributes attributes = osgiManifest.getMainAttributes();
            for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
                effectiveManifest.attributes(WrapUtil.toMap(entry.getKey().toString(), (String) entry.getValue()));
            }
            effectiveManifest.attributes(this.getAttributes());
            for (Map.Entry<String, Attributes> ent : getSections().entrySet()) {
                effectiveManifest.attributes(ent.getValue(), ent.getKey());
            }
            if (classesDir != null) {
                long mod = classesDir.lastModified();
                if (mod > 0) {
                    effectiveManifest.getAttributes().put(Analyzer.BND_LASTMODIFIED, mod);
                }
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return getEffectiveManifestInternal(effectiveManifest);
    }

    private void setAnalyzerProperties(Analyzer analyzer) throws IOException {
        for (Map.Entry<String, Object> attribute : getAttributes().entrySet()) {
            String key = attribute.getKey();
            if (!"Manifest-Version".equals(key)) {
                analyzer.setProperty(key, attribute.getValue().toString());
            }
        }
        Set<String> instructionNames = instructions.keySet();
        if (!instructionNames.contains(Analyzer.IMPORT_PACKAGE)) {
            analyzer.setProperty(Analyzer.IMPORT_PACKAGE,
                    "*, !org.apache.ant.*, !org.junit.*, !org.jmock.*, !org.easymock.*, !org.mockito.*");
        }
        if(!instructionNames.contains(Analyzer.BUNDLE_VERSION)){
            analyzer.setProperty(Analyzer.BUNDLE_VERSION, getVersion());
        }
        if(!instructionNames.contains(Analyzer.BUNDLE_NAME)){
            analyzer.setProperty(Analyzer.BUNDLE_NAME, getName());
        }
        if(!instructionNames.contains(Analyzer.BUNDLE_SYMBOLICNAME)){
            analyzer.setProperty(Analyzer.BUNDLE_SYMBOLICNAME, getSymbolicName());
        }
        if (!instructionNames.contains(Analyzer.EXPORT_PACKAGE)) {
            analyzer.setProperty(Analyzer.EXPORT_PACKAGE, "*;-noimport:=false;version=" + getVersion());
        }
        for (String instructionName : instructionNames) {
            String list = createPropertyStringFromList(instructionValue(instructionName));
            if (list != null && list.length() > 0) {
                analyzer.setProperty(instructionName, list);
            }
        }

        analyzer.setJar(getClassesDir());

        analyzer.setClasspath(getClasspath().getFiles().toArray(new File[getClasspath().getFiles().size()]));
    }

    private String instructionValueString(String instructionName) {
        List<String> values = instructionValue(instructionName);
        if (values == null || values.isEmpty()) {
            return null;
        } else {
            return createPropertyStringFromList(values);
        }
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

    public OsgiManifest instructionReplace(String name, String... values) {
        if (values.length == 0 || (values.length == 1 && values[0] == null)) {
            instructions.remove(name);
        } else {
            if (instructions.get(name) == null) {
                instructions.put(name, new ArrayList<String>());
            }
            List<String> instructionsForName = instructions.get(name);
            instructionsForName.clear();
            Collections.addAll(instructionsForName, values);
        }

        return this;
    }

    public Map<String, List<String>> getInstructions() {
        return instructions;
    }

    private String createPropertyStringFromList(List<String> valueList) {
        return valueList == null || valueList.isEmpty() ? null : GUtil.join(valueList, ",");
    }

    public String getSymbolicName() {
        return instructionValueString(Analyzer.BUNDLE_SYMBOLICNAME);
    }

    public void setSymbolicName(String symbolicName) {
        instructionReplace(Analyzer.BUNDLE_SYMBOLICNAME, symbolicName);
    }

    public String getName() {
        return instructionValueString(Analyzer.BUNDLE_NAME);
    }

    public void setName(String name) {
        instructionReplace(Analyzer.BUNDLE_NAME, name);
    }

    public String getVersion() {
        return instructionValueString(Analyzer.BUNDLE_VERSION);
    }

    public void setVersion(String version) {
        instructionReplace(Analyzer.BUNDLE_VERSION, version);
    }

    public String getDescription() {
        return instructionValueString(Analyzer.BUNDLE_DESCRIPTION);
    }

    public void setDescription(String description) {
        instructionReplace(Analyzer.BUNDLE_DESCRIPTION, description);
    }

    public String getLicense() {
        return instructionValueString(Analyzer.BUNDLE_LICENSE);
    }

    public void setLicense(String license) {
        instructionReplace(Analyzer.BUNDLE_LICENSE, license);
    }

    public String getVendor() {
        return instructionValueString(Analyzer.BUNDLE_VENDOR);
    }

    public void setVendor(String vendor) {
        instructionReplace(Analyzer.BUNDLE_VENDOR, vendor);
    }

    public String getDocURL() {
        return instructionValueString(Analyzer.BUNDLE_DOCURL);
    }

    public void setDocURL(String docURL) {
        instructionReplace(Analyzer.BUNDLE_DOCURL, docURL);
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
