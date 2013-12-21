/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.gradleplugin.foundation.settings;

/**
 * Something that can be serialized to an XML structure. This is meant to store any preferences or settings (lightweight and heavyweight).
 */
public interface SettingsSerializable {
    /**
     * Call this to saves the current settings.
     *
     * @param settings where you save the settings.
     */
    public void serializeOut(SettingsNode settings);

    /**
     * Call this to read in this object's settings. The reverse of serializeOut.
     *
     * @param settings where you read your settings.
     */
    public void serializeIn(SettingsNode settings);
}
