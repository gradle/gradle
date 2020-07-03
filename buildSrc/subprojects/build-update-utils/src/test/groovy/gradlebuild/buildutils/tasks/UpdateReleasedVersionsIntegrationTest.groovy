/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.buildutils.tasks

import gradlebuild.buildutils.model.ReleasedVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.text.SimpleDateFormat

class UpdateReleasedVersionsIntegrationTest extends Specification {

    def format = new SimpleDateFormat('yyyyMMddHHmmssZ')

    @Rule
    TemporaryFolder tmpDir

    def setup() {
        format.timeZone = TimeZone.getTimeZone("UTC")
    }

    def "updated released version file has expected format"() {
        given:
        def releasedVersionsFile = tmpDir.newFile("released-versions.json")
        releasedVersionsFile << '''{
  "latestReleaseSnapshot": {
    "version": "6.6-20200702230251+0000",
    "buildTime": "20200702230251+0000"
  },
  "latestRc": {
    "version": "6.6-rc-1",
    "buildTime": "20200623122834+0000"
  },
  "finalReleases": [
    {
      "version": "6.5.1",
      "buildTime": "20200630063247+0000"
    },
    {
      "version": "6.5",
      "buildTime": "20200602204621+0000"
    },
    {
      "version": "6.4.1",
      "buildTime": "20200515194340+0000"
    },
    {
      "version": "6.4",
      "buildTime": "20200505191855+0000"
    }
  ]
}'''
        def newSnapshot = snapshot('6.6')
        def expectedFileContent = releasedVersionsFile.text.replace('20200702230251+0000', newSnapshot.buildTime)

        when:
        UpdateReleasedVersions.@Companion.updateReleasedVersionFile(releasedVersionsFile, newSnapshot) == ''

        then:
        releasedVersionsFile.text == expectedFileContent
    }

    ReleasedVersion releasedVersion(String version, long date = System.currentTimeMillis()) {
        new ReleasedVersion(version, format.format(new Date(date)))
    }

    ReleasedVersion snapshot(String baseVersion, long date = System.currentTimeMillis()) {
        releasedVersion("${baseVersion}-${format.format(new Date(date))}", date)
    }
}
