/*
 * Copyright 2009 the original author or authors.
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

import aQute.lib.osgi.Jar;
import aQute.lib.osgi.Analyzer;

import java.io.IOException;
import java.util.Map;

public class ContainedVersionAnalyzer extends Analyzer {
    public Map analyzeBundleClasspath(Jar dot, Map bundleClasspath, Map contained, Map referred, Map uses)
            throws IOException {
        Map classSpace = super.analyzeBundleClasspath(dot, bundleClasspath, contained, referred, uses);
        String bundleVersion = getProperties().getProperty(BUNDLE_VERSION);

        for (Object o : contained.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            if (bundleVersion != null) {
                Map values = (Map) entry.getValue();
                values.put("version", bundleVersion);
            }
        }
        return classSpace;
    }
}