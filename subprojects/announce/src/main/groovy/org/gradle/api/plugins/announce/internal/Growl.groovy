/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.plugins.announce.Announcer
import org.gradle.api.Project
import org.gradle.util.Jvm
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class Growl implements Announcer {
    private final Project project

    Growl(Project project) {
        this.project = project
    }

    void send(String title, String message) {
        if (Jvm.current().supportsAppleScript) {
            String script = """
tell application "GrowlHelperApp"
register as application "Gradle" all notifications {"Build Notification"} default notifications {"Build Notification"}
notify with name "Build Notification" title "${escape(title)}" description "${escape(message)}" application name "Gradle"
end tell
"""

            ScriptEngineManager mgr = new ScriptEngineManager();
            ScriptEngine engine = mgr.getEngineByName("AppleScript");
            try {
                engine.eval(script)
            } catch (Throwable t) {
                System.out.println "SCRIPT: [$script]"
                t.printStackTrace(System.out)
                throw t
            };
        } else {
            project.exec {
                executable 'growlnotify'
                args '-m', message, title
            }
        }
    }

    private def escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r")
    }
}
