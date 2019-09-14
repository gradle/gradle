/*
 * Copyright 2019 the original author or authors.
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

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.gradle.api.JavaVersion;
import org.gradle.api.java.archives.internal.generators.GeneratorDescriptors;
import org.gradle.api.java.archives.internal.generators.TupleGenerator;
import org.junit.Assert;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

@RunWith(JUnitQuickcheck.class)
public class ManifestTheoryTest {
    private static final int END_OF_FILE_SYMBOL = 26;

    enum EntryOrder {
        COMPARE_ORDER, IGNORE_ORDER
    }

    /**
     * Test verifies that write->read roundtrip does not corrupt DefaultManifest.
     *
     * @param manifestBuilder manifest builder
     * @throws UnsupportedEncodingException if unsupported encoding is used
     */
    @Property(maxShrinks = 10000, maxShrinkDepth = 10000)
    public void storeSucceeds(
        @From(TupleGenerator.class)
            GeneratorDescriptors.ManifestBuilder manifestBuilder,
        boolean add26AtTheEnd
        ) throws UnsupportedEncodingException {
        DefaultManifest manifest = manifestBuilder.build();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        manifest.writeTo(baos);
        if (add26AtTheEnd) {
            baos.write(END_OF_FILE_SYMBOL);
        }

        byte[] buf = baos.toByteArray();
        DefaultManifest clone = readDefaultManifest(manifest, buf);

        assertEquals(manifest, buf, EntryOrder.COMPARE_ORDER, clone);
    }

    private void assertEquals(DefaultManifest expected, byte[] buf, EntryOrder entryOrder, DefaultManifest actual) throws UnsupportedEncodingException {
        assertEquals(expected, buf, entryOrder, actual.getAttributes(), actual.getSections());
    }

    @SuppressWarnings("rawtypes")
    private void assertEquals(DefaultManifest expected, byte[] buf, EntryOrder entryOrder, Map mainAttributes, Map sections) throws UnsupportedEncodingException {
        try {
            if (entryOrder == EntryOrder.COMPARE_ORDER) {
                Assert.assertEquals(expected.getAttributes().toString(), mainAttributes.toString());
                Assert.assertEquals(expected.getSections().toString(), sections.toString());
            } else {
                Assert.assertEquals(expected.getAttributes(), mainAttributes);
                Assert.assertEquals(expected.getSections(), sections);
            }
        } catch (AssertionError ae) {
            ae.addSuppressed(new Throwable("Content: <<" + new String(buf, expected.getContentCharset()) + ">>"));
        }
    }

    /**
     * Test verifies the resulting manifest can be read with Java's implementation.
     *
     * @param manifestBuilder manifest builder
     * @throws IOException if something goes wrong
     */
    @Property(maxShrinks = 10000, maxShrinkDepth = 10000)
    public void readsWithJavasManifest(
        @From(TupleGenerator.class)
            GeneratorDescriptors.ManifestBuilder manifestBuilder) throws IOException {
        DefaultManifest manifest = manifestBuilder.build();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        manifest.writeTo(baos);

        byte[] buf = baos.toByteArray();
        Manifest javaManifest = new Manifest(new ByteArrayInputStream(buf));

        // OpenJDK 8 is known to use HashMap, so it shuffles attributes
        EntryOrder compareEntryOrder = JavaVersion.current().isJava11Compatible() ? EntryOrder.COMPARE_ORDER : EntryOrder.IGNORE_ORDER;
        assertEquals(manifest,
            buf,
            compareEntryOrder, fromJavaAttributes(javaManifest.getMainAttributes()),
            fromJavaAttributes(javaManifest.getEntries())
        );
    }

    /**
     * Test writes manifest with Java's implementation, and parses it with DefaultManifest.
     *
     * @param manifestBuilder manifest builder
     * @throws IOException if something goes wrong
     */
    @Property(maxShrinks = 10000, maxShrinkDepth = 10000)
    public void understandsJavasManifest(
        @From(TupleGenerator.class)
            GeneratorDescriptors.ManifestBuilder manifestBuilder) throws IOException {
        Manifest javaManifest = new Manifest();
        // MANIFEST_VERSION is required otherwise Java's manifest produces empty file
        javaManifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        toJavaAttributes(javaManifest.getMainAttributes(), manifestBuilder.mainAttributes);
        for (Map.Entry<String, HashMap<String, String>> entry : manifestBuilder.sections.entrySet()) {
            Attributes a = new Attributes();
            toJavaAttributes(a, entry.getValue());
            javaManifest.getEntries().put(entry.getKey(), a);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javaManifest.write(baos);

        DefaultManifest manifest = manifestBuilder.build();

        byte[] buf = baos.toByteArray();
        DefaultManifest clone = readDefaultManifest(manifest, buf);

        // OpenJDK 8 is known to use HashMap, so it shuffles attributes
        EntryOrder compareEntryOrder = JavaVersion.current().isJava11Compatible() ? EntryOrder.COMPARE_ORDER : EntryOrder.IGNORE_ORDER;
        assertEquals(manifest, buf, compareEntryOrder, clone);
    }

    private void toJavaAttributes(Attributes a, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            a.putValue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Test verifies the resulting manifest can be read with Ant's implementation.
     *
     * @param manifestBuilder manifest builder
     * @throws IOException if something goes wrong
     */
    @Property(maxShrinks = 10000, maxShrinkDepth = 10000)
    public void readsWithAntsManifest(
        @From(TupleGenerator.class)
            GeneratorDescriptors.ManifestBuilder manifestBuilder) throws IOException, ManifestException {
        DefaultManifest manifest = manifestBuilder.build();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        manifest.writeTo(baos);

        byte[] buf = baos.toByteArray();
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(buf));
        org.apache.tools.ant.taskdefs.Manifest antManifest = new org.apache.tools.ant.taskdefs.Manifest(reader);

        assertEquals(manifest, buf, EntryOrder.COMPARE_ORDER, fromAntSection(antManifest.getMainSection()), fromAntSections(antManifest));
    }

    /**
     * Test writes manifest with Java's implementation, and parses it with DefaultManifest.
     *
     * @param manifestBuilder manifest builder
     * @throws IOException if something goes wrong
     */
    @Property(maxShrinks = 10000, maxShrinkDepth = 10000)
    public void understandsAntsManifest(
        @From(TupleGenerator.class)
            GeneratorDescriptors.ManifestBuilder manifestBuilder) throws IOException, ManifestException {
        org.apache.tools.ant.taskdefs.Manifest antManifest = new org.apache.tools.ant.taskdefs.Manifest();
        toAntSection(antManifest.getMainSection(), manifestBuilder.mainAttributes);

        for (Map.Entry<String, HashMap<String, String>> entry : manifestBuilder.sections.entrySet()) {
            org.apache.tools.ant.taskdefs.Manifest.Section s = new org.apache.tools.ant.taskdefs.Manifest.Section();
            s.setName(entry.getKey());
            toAntSection(s, entry.getValue());
            antManifest.addConfiguredSection(s);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            antManifest.write(pw);
        }

        DefaultManifest manifest = manifestBuilder.build();

        byte[] buf = sw.toString().getBytes(manifest.getContentCharset());
        DefaultManifest clone = readDefaultManifest(manifest, buf);

        assertEquals(manifest, buf, EntryOrder.COMPARE_ORDER, clone);
    }

    private void toAntSection(org.apache.tools.ant.taskdefs.Manifest.Section s, Map<String, String> values) throws ManifestException {
        // De-duplicate input values since Ant produces
        // ManifestException: The attribute ".." may not occur more than once in the same section
        ArrayList<Map.Entry<String, String>> entries = new ArrayList<>(values.entrySet());
        // The last entry wins, so we reverse the list and skip "duplicates"
        Collections.reverse(entries);
        Set<String> observedKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : entries) {
            String key = entry.getKey();
            if (observedKeys.add(key)) {
                s.addAttributeAndCheck(new org.apache.tools.ant.taskdefs.Manifest.Attribute(key, entry.getValue()));
            }
        }
    }

    private DefaultManifest readDefaultManifest(DefaultManifest manifest, byte[] buf) throws UnsupportedEncodingException {
        DefaultManifest clone = new DefaultManifest(null);
        ManifestReader mr = new ManifestReader(
            new InputStreamReader(
                new ManifestInputStream(new ByteArrayInputStream(buf)),
                manifest.getContentCharset()));
        try {
            mr.read(clone);
        } catch (IOException e) {
            throw new AssertionError("read failed, contents was <<" + new String(buf, manifest.getContentCharset()) + ">>", e);
        }
        return clone;
    }

    private Map<String, Map<String, String>> fromJavaAttributes(Map<String, Attributes> a) {
        Map<String, Map<String, String>> res = new LinkedHashMap<>((int) (a.size() / 0.75));
        for (Map.Entry<String, Attributes> entry : a.entrySet()) {
            res.put(entry.getKey(), fromJavaAttributes(entry.getValue()));
        }
        return res;
    }

    private Map<String, String> fromJavaAttributes(Attributes a) {
        Map<String, String> res = new LinkedHashMap<>((int) (a.size() / 0.75));
        for (Map.Entry<Object, Object> entry : a.entrySet()) {
            Object key = entry.getKey();
            if (key instanceof Attributes.Name) {
                key = key.toString();
            }
            String stringKey = (String) key;
            String stringValue = (String) entry.getValue();
            res.put(stringKey, stringValue);
        }
        return res;
    }

    private Map<String, Map<String, String>> fromAntSections(org.apache.tools.ant.taskdefs.Manifest manifest) {
        Map<String, Map<String, String>> res = new LinkedHashMap<>();
        Enumeration<String> keys = manifest.getSectionNames();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            res.put(key, fromAntSection(manifest.getSection(key)));
        }
        return res;
    }

    private Map<String, String> fromAntSection(org.apache.tools.ant.taskdefs.Manifest.Section section) {
        Map<String, String> res = new LinkedHashMap<>();
        Enumeration<String> keys = section.getAttributeKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            res.put(key, section.getAttributeValue(key));
        }
        return res;
    }
}
