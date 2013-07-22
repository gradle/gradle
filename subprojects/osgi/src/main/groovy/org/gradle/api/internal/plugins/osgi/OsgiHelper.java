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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePluginConvention;

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OsgiHelper {
    /**
     * Bundle-Version must match this pattern
     */
    private static final Pattern OSGI_VERSION_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+(\\.[0-9]+(\\.[0-9A-Za-z_-]+)?)?)?");

    private static final Pattern ONLY_NUMBERS = Pattern.compile("[0-9]+");
    private static final Pattern QUALIFIER = Pattern.compile("[0-9A-Za-z_\\-]*");

    /**
     * Get the symbolic name as group + "." + archivesBaseName, with the following exceptions
     * <ul>
     * <li>
     * if group has only one section (no dots) and archivesBaseName is not null then the first package
     * name with classes is returned. eg. commons-logging:commons-logging -> org.apache.commons.logging
     * </li>
     * <li>
     * if archivesBaseName is equal to last section of group then group is returned.
     * eg. org.gradle:gradle -> org.gradle
     * </li>
     * <li>
     * if archivesBaseName starts with last section of group that portion is removed.
     * eg. org.gradle:gradle-core -> org.gradle.core
     * </li>
     * <li>
     * if archivesBaseName starts with the full group, the archivesBaseName is return,
     * e.g. org.gradle:org.gradle.core -> org.gradle.core
     * </li>
     * </ul>
     *
     * @param project The project being processed.
     *
     * @return Returns the SymbolicName that should be used for the bundle.
     */
    public String getBundleSymbolicName(Project project) {
        String group = project.getGroup().toString();
        String archiveBaseName = project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName();
        if (archiveBaseName.startsWith(group)) {
            return archiveBaseName;
        }
        int i = group.lastIndexOf('.');
        String lastSection = group.substring(++i);
        if (archiveBaseName.equals(lastSection)) {
            return group;
        }
        if (archiveBaseName.startsWith(lastSection)) {
            String artifactId = archiveBaseName.substring(lastSection.length());
            if (Character.isLetterOrDigit(artifactId.charAt(0))) {
                return getBundleSymbolicName(group, artifactId);
            } else {
                return getBundleSymbolicName(group, artifactId.substring(1));
            }
        }
        return getBundleSymbolicName(group, archiveBaseName);
    }

    private String getBundleSymbolicName(String groupId, String artifactId) {
        return groupId + "." + artifactId;
    }

    public String getVersion(String version) {

        /* if it's already OSGi compliant don't touch it */
        final Matcher m = OSGI_VERSION_PATTERN.matcher(version);
        if (m.matches()) {
            return version;
        }

        int group = 0;
        boolean groupToken = true;
        String[] groups = new String[4];
        groups[0] = "0";
        groups[1] = "0";
        groups[2] = "0";
        groups[3] = "";
        StringTokenizer st = new StringTokenizer(version, ",./;'?:\\|=+-_*&^%$#@!~", true);
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (groupToken) {
                if (group < 3) {
                    if (ONLY_NUMBERS.matcher(token).matches()) {
                        groups[group++] = token;
                        groupToken = false;
                    } else {
                        // if not a number, i.e. 2.ABD
                        groups[3] = token + fillQualifier(st);
                    }
                } else {
                    // Last group; what ever is left take that replace all characters that are not alphanum or '_' or '-'
                    groups[3] = token + fillQualifier(st);
                }
            } else {
                // If a delimiter; if dot, swap to groupToken, otherwise the rest belongs in qualifier.
                if (".".equals(token)) {
                    groupToken = true;
                } else {
                    groups[3] = fillQualifier(st);
                }
            }
        }
        String ver = groups[0] + "." + groups[1] + "." + groups[2];
        String result;
        if (groups[3].length() > 0) {
            result = ver + "." + groups[3];
        } else {
            result = ver;
        }
        if (!OSGI_VERSION_PATTERN.matcher(result).matches()) {
            throw new GradleException("OSGi plugin unable to convert version to a compliant version");
        }
        return result;
    }

    private String fillQualifier(StringTokenizer st) {
        StringBuilder buf = new StringBuilder();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (QUALIFIER.matcher(token).matches()) {
                buf.append(token);
            } else {
                buf.append("_");
            }
        }
        return buf.toString();
    }
}
