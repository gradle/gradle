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
package org.gradle.api.plugins.announce.internal.jdk6

import org.gradle.api.plugins.announce.internal.AnnouncerUnavailableException
import org.gradle.api.plugins.announce.internal.Growl
import org.gradle.api.plugins.announce.internal.IconProvider

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class AppleScriptBackedGrowlAnnouncer extends Growl {
    private final IconProvider iconProvider
    private final ScriptEngine engine;

    AppleScriptBackedGrowlAnnouncer(IconProvider iconProvider) {
        this.iconProvider = iconProvider;
        ScriptEngineManager mgr = new ScriptEngineManager();

        engine = mgr.getEngineByName("AppleScript");
        if (engine == null) {
            engine = mgr.getEngineByName("AppleScriptEngine");
        }
        if (engine == null) {
            throw new AnnouncerUnavailableException("AppleScript engine not available on used JVM")
        }
    }

    void send(String title, String message) {
        String isRunning = """
tell application "System Events"
set isRunning to count of (every process whose bundle identifier is "com.Growl.GrowlHelperApp") > 0
end tell
return isRunning
"""
        def value = engine.eval(isRunning)
        if (value == 0) {
            throw new AnnouncerUnavailableException("Growl is not running.")
        }

        def icon = iconProvider.getIcon(48, 48)
        def iconDef = icon ? "image from location ((POSIX file \"${icon.absolutePath}\") as string) as alias" : ""
        def script = """
tell application id "com.Growl.GrowlHelperApp"
register as application "Gradle" all notifications {"Build Notification"} default notifications {"Build Notification"}
notify with name "Build Notification" title "${escape(title)}" description "${escape(message)}" application name "Gradle"${iconDef}
end tell
"""

        engine.eval(script)
    }

    private def escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r")
    }
}
