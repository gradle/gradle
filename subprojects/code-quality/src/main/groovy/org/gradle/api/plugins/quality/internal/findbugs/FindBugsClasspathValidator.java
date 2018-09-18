/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.findbugs;

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.util.VersionNumber;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindBugsClasspathValidator {

    private final JavaVersion javaVersion;

    public FindBugsClasspathValidator(JavaVersion javaVersion) {
        this.javaVersion = javaVersion;
    }

    public void validateClasspath(Iterable<String> fileNamesOnClasspath) {
        VersionNumber v = getFindbugsVersion(fileNamesOnClasspath);
        boolean java8orMore = javaVersion.compareTo(JavaVersion.VERSION_1_7) > 0;
        boolean findbugs2orLess = v.getMajor() < 3;
        if (java8orMore && findbugs2orLess) {
            throw new FindBugsVersionTooLowException("The version of FindBugs (" + v + ") inferred from FindBugs classpath is too low to work with currently used Java version (" + javaVersion + ")."
                    + " Please use higher version of FindBugs. Inspected FindBugs classpath: " + fileNamesOnClasspath);
        }
    }

    static class FindBugsVersionTooLowException extends GradleException {
        FindBugsVersionTooLowException(String message) {
            super(message);
        }
    }

    private VersionNumber getFindbugsVersion(Iterable<String> classpath) {
        for (String f: classpath) {
            Matcher m = Pattern.compile("findbugs-(\\d+.*)\\.jar").matcher(f);
            if (m.matches()) {
                return VersionNumber.parse(m.group(1));
            }
        }
        throw new GradleException("Unable to infer the version of FindBugs from currently specified FindBugs classpath: " + classpath);
    }
}
