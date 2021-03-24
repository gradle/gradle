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

package org.gradle.api.publish.ivy

import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule

class IvySftpLegacyPublishIntegrationTest extends AbstractIvyRemoteLegacyPublishIntegrationTest {
    @Rule
    SFTPServer server = new SFTPServer(temporaryFolder)

    def setup() {
        // SFTP test fixture does not handle parallel resolution requests
        executer.beforeExecute {
            it.withArgument("--max-workers=1")
        }
    }
}
