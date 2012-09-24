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

package org.gradle.integtests.fixtures

import org.gradle.internal.os.OperatingSystem

/**
 * by Szczepan Faber, created at: 9/24/12
 */
class KillProcessAvailability {

    final static CAN_KILL

    static {
        if (OperatingSystem.current().isUnix()) {
            CAN_KILL = true
        } else if (OperatingSystem.current().isWindows()) {
            //On some windowses, taskkill does not seem to work when triggered from java
            //On our CIs this works fine
            CAN_KILL = "taskkill.exe /?".execute().waitFor() == 0
        } else {
            CAN_KILL = false
        }
    }
}
