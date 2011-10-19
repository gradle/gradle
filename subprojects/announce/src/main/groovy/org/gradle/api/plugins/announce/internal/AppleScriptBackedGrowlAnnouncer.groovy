/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.announce.internal

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class AppleScriptBackedGrowlAnnouncer extends Growl {
    void send(String title, String message) {
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName("AppleScript");

        String isRunning = """
tell application "System Events"
set isRunning to count of (every process whose name is "GrowlHelperApp") > 0
end tell
return isRunning
"""
        def value = engine.eval(isRunning)
        if (value == 0) {
            throw new AnnouncerUnavailableException("Growl is not running.")
        }

        String script = """
tell application "GrowlHelperApp"
register as application "Gradle" all notifications {"Build Notification"} default notifications {"Build Notification"}
notify with name "Build Notification" title "${escape(title)}" description "${escape(message)}" application name "Gradle"
end tell
"""

        engine.eval(script)
    }

    private def escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r")
    }
}
