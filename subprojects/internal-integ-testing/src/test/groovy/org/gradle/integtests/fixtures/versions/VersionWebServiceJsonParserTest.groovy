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

import spock.lang.Specification

import static org.gradle.integtests.fixtures.versions.ReleasedGradleVersion.Type.*

class VersionWebServiceJsonParserTest extends Specification {

    def "can read"() {
        when:
        def versions = parse('''[{
	"version":"1.4-20130107230014+0000",
	"buildTime":"20130107230014+0000",
	"current":false,
	"snapshot":true,
	"nightly":true,
	"activeRc":false,
	"rcFor":"",
	"broken":false,
	"downloadUrl":"http:\\/\\/services.gradle.org\\/distributions-snapshots\\/gradle-1.4-20130107230014+0000-bin.zip"
},{
	"version":"1.3-rc-1",
	"buildTime":"20121120113738+0000",
	"current":false,
	"snapshot":false,
	"nightly":false,
	"activeRc":true,
	"rcFor":"1.3",
	"broken":false,
	"downloadUrl":"http:\\/\\/services.gradle.org\\/distributions\\/gradle-1.3-rc-1-bin.zip"
},{
	"version":"1.2",
	"buildTime":"20121115155343+0000",
	"current":true,
	"snapshot":false,
	"nightly":false,
	"activeRc":false,
	"rcFor":"",
	"broken":false,
	"downloadUrl":"http:\\/\\/services.gradle.org\\/distributions\\/gradle-1.2-bin.zip"
},{
	"version":"1.1",
	"buildTime":"20120731132432+0000",
	"current":false,
	"snapshot":false,
	"nightly":false,
	"activeRc":false,
	"rcFor":"",
	"broken":false,
	"downloadUrl":"http:\\/\\/services.gradle.org\\/distributions\\/gradle-1.1-bin.zip"
},{
	"version":"1.1-rc-2",
	"buildTime":"20120726075103+0000",
	"current":false,
	"snapshot":false,
	"nightly":false,
	"activeRc":false,
	"rcFor":"1.1",
	"broken":false,
	"downloadUrl":"http:\\/\\/services.gradle.org\\/distributions\\/gradle-1.1-rc-2-bin.zip"
},{
	"version":"1.1-rc-1",
	"buildTime":"20120724134404+0000",
	"current":false,
	"snapshot":false,
	"nightly":false,
	"activeRc":false,
	"rcFor":"1.1",
	"broken":false,
	"downloadUrl":"http:\\/\\/services.gradle.org\\/distributions\\/gradle-1.1-rc-1-bin.zip"
},{
	"version":"1.0",
	"buildTime":"20120612025621+0200",
	"current":false,
	"snapshot":false,
	"nightly":false,
	"activeRc":false,
	"rcFor":"",
	"broken":false,
	"downloadUrl":"http:\\/\\/services.gradle.org\\/distributions\\/gradle-1.0-bin.zip"
}]''')

        then:
        versions.size() == 7
        versions.findAll { it.type == FINAL }.size() == 3
        versions.findAll { it.type == RELEASE_CANDIDATE }.size() == 3
        versions.find { it.type == RELEASE_CANDIDATE && it.current }.version.version == "1.3-rc-1"
        versions.find { it.type == FINAL && it.current }.version.version == "1.2"
        versions.find { it.type == NIGHTLY }.current
    }

    def parse(String text) {
        return new VersionWebServiceJsonParser(new org.gradle.internal.Factory<Reader>() {
            public Reader create() {
                return new StringReader(text);
            }
        }).create();
    }
}
