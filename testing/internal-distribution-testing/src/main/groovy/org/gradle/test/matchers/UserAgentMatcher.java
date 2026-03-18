/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.test.matchers;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matchers;

public class UserAgentMatcher extends BaseMatcher<String> {

    private final String applicationName;
    private final String version;

    public UserAgentMatcher(String applicationName, String version) {
        this.applicationName = applicationName;
        this.version = version;
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(expectedUserAgentString());
    }

    public static UserAgentMatcher matchesNameAndVersion(String applicationName, String version) {
        return new UserAgentMatcher(applicationName, version);
    }

    @Override
    public boolean matches(Object o) {
        String testString = expectedUserAgentString();
        return Matchers.startsWith(testString).matches(o);
    }

    private String expectedUserAgentString() {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        return String.format("%s/%s (%s;%s;%s)", applicationName, version, osName, osVersion, osArch);
    }
}
