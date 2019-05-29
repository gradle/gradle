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

import aQute.bnd.osgi.Analyzer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.plugins.osgi.OsgiManifest;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.Manifest;

@SuppressWarnings("deprecation")
public class DefaultOsgiManifest extends DefaultManifest implements OsgiManifest {

    // Because these properties can be convention mapped we need special handling in here.
    // If you add another one of these “modelled” properties, you need to update:
    // - maybeAppendModelledInstruction()
    // - maybePrependModelledInstruction()
    // - maybeSetModelledInstruction()
    // - getModelledInstructions()
    // - instructionValue()
    private String symbolicName;
    private String name;
    private String version;
    private String description;
    private String license;
    private String vendor;
    private String docURL;

    private File classesDir;

    private Factory<ContainedVersionAnalyzer> analyzerFactory = new DefaultAnalyzerFactory();

    private Map<String, List<String>> unmodelledInstructions = new HashMap<String, List<String>>();

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


        Map<String, List<String>> instructions = getInstructions();
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

        analyzer.setClasspath(getClasspath().getFiles().toArray(new File[0]));
    }

    @Override
    public List<String> instructionValue(String instructionName) {
        if (instructionName.equals(Analyzer.BUNDLE_SYMBOLICNAME)) {
            return createListFromPropertyString(getSymbolicName());
        } else if (instructionName.equals(Analyzer.BUNDLE_NAME)) {
            return createListFromPropertyString(getName());
        } else if (instructionName.equals(Analyzer.BUNDLE_VERSION)) {
            return createListFromPropertyString(getVersion());
        } else if (instructionName.equals(Analyzer.BUNDLE_DESCRIPTION)) {
            return createListFromPropertyString(getDescription());
        } else if (instructionName.equals(Analyzer.BUNDLE_LICENSE)) {
            return createListFromPropertyString(getLicense());
        } else if (instructionName.equals(Analyzer.BUNDLE_VENDOR)) {
            return createListFromPropertyString(getVendor());
        } else if (instructionName.equals(Analyzer.BUNDLE_DOCURL)) {
            return createListFromPropertyString(getDocURL());
        } else {
            return unmodelledInstructions.get(instructionName);
        }
    }

    @Override
    public OsgiManifest instruction(String name, String... values) {
        if (!maybeAppendModelledInstruction(name, values)) {
            if (unmodelledInstructions.get(name) == null) {
                unmodelledInstructions.put(name, new ArrayList<String>());
            }
            unmodelledInstructions.get(name).addAll(Arrays.asList(values));
        }

        return this;
    }

    private String appendValues(String existingValues, String... toPrepend) {
        List<String> parts = createListFromPropertyString(existingValues);
        if (parts == null) {
            return createPropertyStringFromArray(toPrepend);
        } else {
            parts.addAll(Arrays.asList(toPrepend));
            return createPropertyStringFromList(parts);
        }
    }

    private boolean maybeAppendModelledInstruction(String name, String... values) {
        if (name.equals(Analyzer.BUNDLE_SYMBOLICNAME)) {
            setSymbolicName(appendValues(getSymbolicName(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_NAME)) {
            setName(appendValues(getName(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_VERSION)) {
            setVersion(appendValues(getVersion(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_DESCRIPTION)) {
            setDescription(appendValues(getDescription(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_LICENSE)) {
            setLicense(appendValues(getLicense(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_VENDOR)) {
            setVendor(appendValues(getVendor(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_DOCURL)) {
            setDocURL(appendValues(getDocURL(), values));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public OsgiManifest instructionFirst(String name, String... values) {
        if (!maybePrependModelledInstruction(name, values)) {
            if (unmodelledInstructions.get(name) == null) {
                unmodelledInstructions.put(name, new ArrayList<String>());
            }
            unmodelledInstructions.get(name).addAll(0, Arrays.asList(values));
        }
        return this;
    }

    private String prependValues(String existingValues, String... toPrepend) {
        List<String> parts = createListFromPropertyString(existingValues);
        if (parts == null) {
            return createPropertyStringFromArray(toPrepend);
        } else {
            parts.addAll(0, Arrays.asList(toPrepend));
            return createPropertyStringFromList(parts);
        }
    }

    private boolean maybePrependModelledInstruction(String name, String... values) {
        if (name.equals(Analyzer.BUNDLE_SYMBOLICNAME)) {
            setSymbolicName(prependValues(getSymbolicName(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_NAME)) {
            setName(prependValues(getName(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_VERSION)) {
            setVersion(prependValues(getVersion(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_DESCRIPTION)) {
            setDescription(prependValues(getDescription(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_LICENSE)) {
            setLicense(prependValues(getLicense(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_VENDOR)) {
            setVendor(prependValues(getVendor(), values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_DOCURL)) {
            setDocURL(prependValues(getDocURL(), values));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public OsgiManifest instructionReplace(String name, String... values) {
        if (!maybeSetModelledInstruction(name, values)) {
            if (values.length == 0 || (values.length == 1 && values[0] == null)) {
                unmodelledInstructions.remove(name);
            } else {
                if (unmodelledInstructions.get(name) == null) {
                    unmodelledInstructions.put(name, new ArrayList<String>());
                }
                List<String> instructionsForName = unmodelledInstructions.get(name);
                instructionsForName.clear();
                Collections.addAll(instructionsForName, values);
            }
        }

        return this;
    }

    private boolean maybeSetModelledInstruction(String name, String... values) {
        if (name.equals(Analyzer.BUNDLE_SYMBOLICNAME)) {
            setSymbolicName(createPropertyStringFromArray(values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_NAME)) {
            setName(createPropertyStringFromArray(values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_VERSION)) {
            setVersion(createPropertyStringFromArray(values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_DESCRIPTION)) {
            setDescription(createPropertyStringFromArray(values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_LICENSE)) {
            setLicense(createPropertyStringFromArray(values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_VENDOR)) {
            setVendor(createPropertyStringFromArray(values));
            return true;
        } else if (name.equals(Analyzer.BUNDLE_DOCURL)) {
            setDocURL(createPropertyStringFromArray(values));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, List<String>> getInstructions() {
        Map<String, List<String>> instructions = new HashMap<String, List<String>>();
        instructions.putAll(unmodelledInstructions);
        instructions.putAll(getModelledInstructions());
        return instructions;
    }

    private String createPropertyStringFromArray(String... valueList) {
        return createPropertyStringFromList(Arrays.asList(valueList));
    }

    private String createPropertyStringFromList(List<String> valueList) {
        return valueList == null || valueList.isEmpty() ? null : CollectionUtils.join(",", valueList);
    }

    private List<String> createListFromPropertyString(String propertyString) {
        return propertyString == null || propertyString.length() == 0 ? null : new LinkedList<String>(Arrays.asList(propertyString.split(",")));
    }

    private Map<String, List<String>> getModelledInstructions() {
        Map<String, List<String>> modelledInstructions = new HashMap<String, List<String>>();
        modelledInstructions.put(Analyzer.BUNDLE_SYMBOLICNAME, createListFromPropertyString(symbolicName));
        modelledInstructions.put(Analyzer.BUNDLE_NAME, createListFromPropertyString(name));
        modelledInstructions.put(Analyzer.BUNDLE_VERSION, createListFromPropertyString(version));
        modelledInstructions.put(Analyzer.BUNDLE_DESCRIPTION, createListFromPropertyString(description));
        modelledInstructions.put(Analyzer.BUNDLE_LICENSE, createListFromPropertyString(license));
        modelledInstructions.put(Analyzer.BUNDLE_VENDOR, createListFromPropertyString(vendor));
        modelledInstructions.put(Analyzer.BUNDLE_DOCURL, createListFromPropertyString(docURL));

        return CollectionUtils.filter(modelledInstructions, new Spec<Map.Entry<String, List<String>>>() {
            @Override
            public boolean isSatisfiedBy(Map.Entry<String, List<String>> element) {
                return element.getValue() != null;
            }
        });
    }

    @Override
    public String getSymbolicName() {
        return symbolicName;
    }

    @Override
    public void setSymbolicName(String symbolicName) {
        this.symbolicName = symbolicName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getLicense() {
        return license;
    }

    @Override
    public void setLicense(String license) {
        this.license = license;
    }

    @Override
    public String getVendor() {
        return vendor;
    }

    @Override
    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    @Override
    public String getDocURL() {
        return docURL;
    }

    @Override
    public void setDocURL(String docURL) {
        this.docURL = docURL;
    }

    @Override
    public File getClassesDir() {
        return classesDir;
    }

    @Override
    public void setClassesDir(File classesDir) {
        this.classesDir = classesDir;
    }

    @Override
    public FileCollection getClasspath() {
        return classpath;
    }

    @Override
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
