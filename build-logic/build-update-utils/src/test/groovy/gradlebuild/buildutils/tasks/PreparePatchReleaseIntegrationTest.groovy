/*
 * Copyright 2025 the original author or authors.
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
import spock.lang.Specification
import spock.lang.TempDir

class PreparePatchReleaseIntegrationTest extends Specification {

    @TempDir
    File tmpDir

    def "version file is bumped and previous version is added to released versions"() {
        given:
        def versionFile = new File(tmpDir, "version.txt")
        versionFile.text = "9.4.0"

        def releasedVersionsFile = new File(tmpDir, "released-versions.json")
        releasedVersionsFile << '''{
  "latestReleaseSnapshot": {
    "version": "9.4.1-20260317014409+0000",
    "buildTime": "20260317014409+0000"
  },
  "latestRc": {
    "version": "9.4.0-rc-2",
    "buildTime": "20260227092055+0000"
  },
  "finalReleases": []
}'''

        def previousVersion = new ReleasedVersion("9.4.0", "20260304103600+0000")
        def expectedReleasedVersionsContent = '''{
  "latestReleaseSnapshot": {
    "version": "9.4.1-20260317014409+0000",
    "buildTime": "20260317014409+0000"
  },
  "latestRc": {
    "version": "9.4.0-rc-2",
    "buildTime": "20260227092055+0000"
  },
  "finalReleases": [
    {
      "version": "9.4.0",
      "buildTime": "20260304103600+0000"
    }
  ]
}'''

        when:
        def patchVersion = ReleasedVersionsHelperKt.bumpPatchVersion(versionFile.text.trim())
        versionFile.text = patchVersion
        ReleasedVersionsHelperKt.updateReleasedVersionFile(releasedVersionsFile, previousVersion)

        then:
        versionFile.text == "9.4.1"
        releasedVersionsFile.text == expectedReleasedVersionsContent
    }
}