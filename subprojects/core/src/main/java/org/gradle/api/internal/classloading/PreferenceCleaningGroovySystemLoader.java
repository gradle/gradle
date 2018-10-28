/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.classloading;

import org.gradle.api.GradleException;

import java.lang.reflect.Field;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

public class PreferenceCleaningGroovySystemLoader implements GroovySystemLoader {

    private final Field prefListenerField;
    private final ClassLoader leakingLoader;

    public PreferenceCleaningGroovySystemLoader(ClassLoader leakingLoader) throws Exception {
        this.leakingLoader = leakingLoader;
        prefListenerField = AbstractPreferences.class.getDeclaredField("prefListeners");
        prefListenerField.setAccessible(true);
    }

    @Override
    public void shutdown() {
        try {
            Preferences groovyNode = Preferences.userRoot().node("/org/codehaus/groovy/tools/shell");
            PreferenceChangeListener[] prefListeners = (PreferenceChangeListener[]) prefListenerField.get(groovyNode);
            if (prefListeners == null) {
                return;
            }
            for (PreferenceChangeListener prefListener : prefListeners) {
                ClassLoader prefListenerLoader = prefListener.getClass().getClassLoader();
                if (leakingLoader == prefListenerLoader) {
                    groovyNode.removePreferenceChangeListener(prefListener);
                }
            }
        } catch (Exception e) {
            throw new GradleException("Could not shut down the Groovy system for " + leakingLoader, e);
        }
    }

    @Override
    public void discardTypesFrom(ClassLoader classLoader) {

    }
}
