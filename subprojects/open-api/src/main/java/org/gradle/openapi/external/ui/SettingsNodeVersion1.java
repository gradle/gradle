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
package org.gradle.openapi.external.ui;

import java.util.List;

/**
 * Abstraction of how settings are stored. If you're implementing this, see SettingsNode for more information.
 *
 * This is a mirror of SettingsNode inside Gradle, but this is meant to aid backward and forward compatibility by shielding you from direct changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface SettingsNodeVersion1 {
    public void setName(String name);

    public String getName();

    public void setValue(String value);

    public String getValue();

    public void setValueOfChild(String name, String value);

    public String getValueOfChild(String name, String defaultValue);

    public int getValueOfChildAsInt(String name, int defaultValue);

    public void setValueOfChildAsInt(String name, int value);

    public boolean getValueOfChildAsBoolean(String name, boolean defaultValue);

    public void setValueOfChildAsBoolean(String name, boolean value);

    public long getValueOfChildAsLong(String name, long defaultValue);

    public void setValueOfChildAsLong(String name, long value);

    public List<SettingsNodeVersion1> getChildNodes();

    public List<SettingsNodeVersion1> getChildNodes(String name);

    public SettingsNodeVersion1 addChild(String name);

    public SettingsNodeVersion1 addChildIfNotPresent(String name);

    public SettingsNodeVersion1 getChildNode(String name);

    public SettingsNodeVersion1 getNodeAtPath(String... pathPortions);

    public void removeFromParent();

    public void removeAllChildren();
}
