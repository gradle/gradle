package org.gradle.api.internal.plugins.osgi;

import aQute.lib.osgi.Jar;
import aQute.lib.osgi.Analyzer;

import java.io.IOException;
import java.util.Map;
import java.util.Iterator;

public class ContainedVersionAnalyzer extends Analyzer {
    public Map analyzeBundleClasspath(Jar dot, Map bundleClasspath, Map contained, Map referred, Map uses)
            throws IOException {
        Map classSpace = super.analyzeBundleClasspath(dot, bundleClasspath, contained, referred, uses);
        String bundleVersion = getProperties().getProperty(BUNDLE_VERSION);

        for (Iterator it = contained.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            if (bundleVersion != null) {
                Map values = (Map) entry.getValue();
                values.put("version", bundleVersion);
            }
        }
        return classSpace;
    }
}