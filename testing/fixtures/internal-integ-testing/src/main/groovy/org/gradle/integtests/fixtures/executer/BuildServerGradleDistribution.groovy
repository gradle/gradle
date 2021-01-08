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

package org.gradle.integtests.fixtures.executer

import org.gradle.test.fixtures.file.TestFile

class BuildServerGradleDistribution extends DownloadableGradleDistribution {
    private String buildNumberVersion

    public BuildServerGradleDistribution(String buildNumberVersion, TestFile versionDir) {
        super(convertToBuildVersion(buildNumberVersion), versionDir)
        this.buildNumberVersion = buildNumberVersion - '#';
    }

    static String convertToBuildVersion(String buildNumberVersion) {
        def url = new URL("http://builds.gradle.org/repository/download/bt45/${buildNumberVersion - "#"}/build-receipt.properties?guest=1")
        def buildReceipt = url.text
        Properties props = new Properties()
        props.load(new StringReader(buildReceipt))
        return props.getProperty("versionNumber");
    }

    @Override
    protected URL getDownloadURL() {
        return new URL("http://builds.gradle.org/repository/download/bt45/${buildNumberVersion}/distributions/gradle-${URLEncoder.encode(version.version, "UTF-8")}-bin.zip?guest=1");
    }
}
