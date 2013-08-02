/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.*;

public class LatestVersionStrategy implements LatestStrategy {
    /**
     * Compares two ModuleRevisionId by their revision. Revisions are compared using an algorithm
     * inspired by PHP version_compare one.
     */
    private final static class VersionComparator implements Comparator<String> {
        public int compare(String version1, String version2) {
            version1 = version1.replaceAll("([a-zA-Z])(\\d)", "$1.$2");
            version1 = version1.replaceAll("(\\d)([a-zA-Z])", "$1.$2");
            version2 = version2.replaceAll("([a-zA-Z])(\\d)", "$1.$2");
            version2 = version2.replaceAll("(\\d)([a-zA-Z])", "$1.$2");

            String[] parts1 = version1.split("[\\._\\-\\+]");
            String[] parts2 = version2.split("[\\._\\-\\+]");

            int i = 0;
            for (; i < parts1.length && i < parts2.length; i++) {
                if (parts1[i].equals(parts2[i])) {
                    continue;
                }
                boolean is1Number = isNumber(parts1[i]);
                boolean is2Number = isNumber(parts2[i]);
                if (is1Number && !is2Number) {
                    return 1;
                }
                if (is2Number && !is1Number) {
                    return -1;
                }
                if (is1Number && is2Number) {
                    return Long.valueOf(parts1[i]).compareTo(Long.valueOf(parts2[i]));
                }
                // both are strings, we compare them taking into account special meaning
                Integer sm1 = SPECIAL_MEANINGS.get(parts1[i].toLowerCase(Locale.US));
                Integer sm2 = SPECIAL_MEANINGS.get(parts2[i].toLowerCase(Locale.US));
                if (sm1 != null) {
                    sm2 = sm2 == null ? new Integer(0) : sm2;
                    return sm1.compareTo(sm2);
                }
                if (sm2 != null) {
                    return new Integer(0).compareTo(sm2);
                }
                return parts1[i].compareTo(parts2[i]);
            }
            if (i < parts1.length) {
                return isNumber(parts1[i]) ? 1 : -1;
            }
            if (i < parts2.length) {
                return isNumber(parts2[i]) ? -1 : 1;
            }
            return 0;
        }

        private boolean isNumber(String str) {
            return str.matches("\\d+");
        }
    }

    /**
     * Compares two ArtifactInfo by their revision. Revisions are compared using an algorithm
     * inspired by PHP version_compare one, unless a dynamic revision is given, in which case the
     * version matcher is used to perform the comparison.
     */
    private final class VersionedElementComparator implements Comparator<Versioned> {
        public int compare(Versioned element1, Versioned element2) {
            String version1 = element1.getVersion();
            String version2 = element2.getVersion();

            /*
             * The revisions can still be not resolved, so we use the current version matcher to
             * know if one revision is dynamic, and in this case if it should be considered greater
             * or lower than the other one. Note that if the version matcher compare method returns
             * 0, it's because it's not possible to know which revision is greater. In this case we
             * consider the dynamic one to be greater, because most of the time it will then be
             * actually resolved and a real comparison will occur.
             */
            if (versionMatcher.isDynamic(version1)) {
                int c = versionMatcher.compare(version1, version2, versionComparator);
                return c >= 0 ? 1 : -1;
            } else if (versionMatcher.isDynamic(version2)) {
                int c = versionMatcher.compare(version2, version1, versionComparator);
                return c >= 0 ? -1 : 1;
            }

            return versionComparator.compare(version1, version2);
        }
    }

    private static final Map<String, Integer> SPECIAL_MEANINGS =
            ImmutableMap.of("def", new Integer(-1), "rc", new Integer(1), "final", new Integer(2));

    private final Comparator<String> versionComparator = new VersionComparator();

    private final Comparator<Versioned> versionedElementComparator = new VersionedElementComparator();

    private VersionMatcher versionMatcher;

    public void setVersionMatcher(VersionMatcher versionMatcher) {
        this.versionMatcher = versionMatcher;
    }

    public <T extends Versioned> List<T> sort(List<T> versions) {
        List<T> result = Lists.newArrayList(versions);
        Collections.sort(result, versionedElementComparator);
        return result;
    }

    public <T extends Versioned> Versioned findLatest(List<T> elements) {
        List<T> sortedVersions = sort(elements);
        return sortedVersions.get(sortedVersions.size() - 1);
    }
}
