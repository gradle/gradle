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

package org.gradle.integtests.fixtures.versions

import groovy.json.JsonSlurper
import org.gradle.util.GradleVersion
import static org.gradle.util.GradleVersion.version

/**
 * by Szczepan Faber, created at: 3/12/12
 */
class VersionsInfo {
    
    String lowestInterestingVersion = "0.8"
    List<String> excludedVersions = ["0.9-rc-1", "0.9-rc-2", "1.0-milestone-4", GradleVersion.current().version]

    Closure getVersionsJson = {
        def fileName = "all-released-versions.json"
        def resource = getClass().classLoader.getResource(fileName)
        if (resource == null) {
            throw new RuntimeException("""Unable to find the released versions information.
The resource '$fileName' was not found.
Most likely, you haven't ran the 'prepareVersionsInfo' task.
If you have trouble running tests from your IDE, please run gradlew idea|eclipse first.
""")
        }
        def jsonText = resource.text
        return new JsonSlurper().parseText(jsonText)
    }

    /**
     * @return the versions we are interested in covering in our cross-compatibility tests.
     * Always contains the current release. May contain the RC. Never contains nightly.
     * Excludes 'current' version. Excludes certain less-interesting versions. The list is ordered,
     * latest version first.
     */
    List<String> getVersions() {
        def json = getVersionsJson()

        def rc = json.find { it.rcFor }
        def current = json.find { it.current }

        def out = new LinkedHashSet<String>()

        //exclude nightly and old versions
        def releases = json.findAll { !it.nightly && version(it.version) >= version(lowestInterestingVersion) &&
                !excludedVersions.contains(it.version) }

        out += releases.collect { it.version }

        if (rc) {
            if (rc.rcFor == current.version) {
                //RC released - remove from list:
                out.remove(rc.version)
            } else {
                //if the RC has not been already released it should be higher then the current
                assert version(rc.version) > version(current.version)
            }
        }

        def sorted = out.sort( { a,b -> version(b).compareTo(version(a)) })
        new LinkedList(sorted)
    }
}
