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

package org.gradle.api.plugins.announce.internal.jdk6;

import org.gradle.api.plugins.announce.internal.AnnouncerUnavailableException;
import org.gradle.api.plugins.announce.internal.Growl;
import org.gradle.api.plugins.announce.internal.IconProvider;
import org.gradle.internal.UncheckedException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;

public class AppleScriptBackedGrowlAnnouncer extends Growl {

    private final IconProvider iconProvider;
    private ScriptEngine engine;

    public AppleScriptBackedGrowlAnnouncer(IconProvider iconProvider) {
        this.iconProvider = iconProvider;
        ScriptEngineManager mgr = new ScriptEngineManager();
        engine = mgr.getEngineByName("AppleScript");
        if (engine == null) {
            engine = mgr.getEngineByName("AppleScriptEngine");
        }
        if (engine == null) {
            throw new AnnouncerUnavailableException("AppleScript engine not available on used JVM");
        }
    }

    @Override
    public void send(String title, String message) {
        String isRunning = "\ntell application \"System Events\"\n"
            + "set isRunning to count of (every process whose bundle identifier is \"com.Growl.GrowlHelperApp\") > 0\n"
            + "end tell\n" + "return isRunning\n";
        try {
            Object value = engine.eval(isRunning);
            if (value.equals(0)) {
                throw new AnnouncerUnavailableException("Growl is not running.");
            }
            final File icon = iconProvider.getIcon(48, 48);
            String iconDef = icon != null ? "image from location ((POSIX file \"" + icon.getAbsolutePath() + "\") as string) as alias" : "";
            String script = "\ntell application id \"com.Growl.GrowlHelperApp\"\n"
                + "register as application \"Gradle\" all notifications {\"Build Notification\"} default notifications {\"Build Notification\"}\n"
                + "notify with name \"Build Notification\" title \"" + escape(title) + "\" description \"" + escape(message)
                + "\" application name \"Gradle\"" + iconDef
                + "\nend tell\n";
            engine.eval(script);
        } catch (ScriptException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r");
    }
}
