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

package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.settings.RuntimeInfo;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.TrackableBase;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.tools.ant.taskdefs.Execute;
import org.codehaus.plexus.interpolation.EnvarBasedValueSource;
import org.codehaus.plexus.interpolation.RegexBasedInterpolator;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil;
import org.gradle.mvn3.org.codehaus.plexus.util.ReaderFactory;
import org.gradle.mvn3.org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

class MavenSettingsLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenSettingsLoader.class);

    Settings loadSettings(File settingsFile) {
        Settings settings = doLoadSettings(settingsFile);

        Settings globalSettings = doLoadSettings(findGlobalSettingsFile());
        SettingsUtils.merge(settings, globalSettings, TrackableBase.GLOBAL_LEVEL);

        if (StringUtils.isEmpty(settings.getLocalRepository())) {
            String location = newFile(System.getProperty("user.home"), ".m2", "repository").getAbsolutePath();
            settings.setLocalRepository(location);
        }
        return settings;
    }

    private Settings doLoadSettings(File settingsFile) {
        Settings settings = null;
        try {
            if (settingsFile != null) {
                log("Loading Maven settings file: " + settingsFile.getPath());
                settings = readSettings(settingsFile);
            }
        } catch (IOException e) {
            log("Error reading settings file '" + settingsFile + "' - ignoring. Error was: " + e.getMessage()
            );
        } catch (XmlPullParserException e) {
            log("Error parsing settings file '" + settingsFile + "' - ignoring. Error was: " + e.getMessage()
            );
        }

        if (settings == null) {
            settings = new Settings();
            RuntimeInfo rtInfo = new RuntimeInfo(settings);
            settings.setRuntimeInfo(rtInfo);
        }

        return settings;
    }

    /**
     * @see org.apache.maven.settings.DefaultMavenSettingsBuilder#readSettings
     */
    private Settings readSettings(File settingsFile)
            throws IOException, XmlPullParserException {
        Settings settings = null;
        Reader reader = null;
        try {
            reader = ReaderFactory.newXmlReader(settingsFile);
            StringWriter sWriter = new StringWriter();

            IOUtil.copy(reader, sWriter);

            String rawInput = sWriter.toString();

            try {
                RegexBasedInterpolator interpolator = new RegexBasedInterpolator();
                interpolator.addValueSource(new EnvarBasedValueSource());

                rawInput = interpolator.interpolate(rawInput, "settings");
            } catch (Exception e) {
                log("Failed to initialize environment variable resolver. Skipping environment substitution in "
                        + "settings.");
            }

            StringReader sReader = new StringReader(rawInput);

            SettingsXpp3Reader modelReader = new SettingsXpp3Reader();

            settings = modelReader.read(sReader);

            RuntimeInfo rtInfo = new RuntimeInfo(settings);

            rtInfo.setFile(settingsFile);

            settings.setRuntimeInfo(rtInfo);
        } finally {
            IOUtil.close(reader);
        }
        return settings;
    }

    private File findGlobalSettingsFile() {
        // look in ${M2_HOME}/conf
        List<String> env = Execute.getProcEnvironment();
        for (String var : env) {
            if (var.startsWith("M2_HOME=")) {
                String m2Home = var.substring("M2_HOME=".length());
                File candidate = newFile(m2Home, "conf", "settings.xml");
                if (candidate.exists()) {
                    return candidate;
                }
                break;
            }
        }
        return null;
    }

    private File newFile(String parent, String subdir, String filename) {
        return new File(new File(parent, subdir), filename);
    }

    protected void log(String message) {
       LOGGER.info(message);
   }
}
