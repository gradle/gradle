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
package org.gradle.integtests.openapi;

import org.gradle.openapi.external.ui.AlternateUIInteractionVersion1;
import org.gradle.openapi.external.ui.DualPaneUIInteractionVersion1;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIInteractionVersion1;

/**
 * This is a test implementation of both SinglePaneUIInteractionVersion1 and DualPaneUIInteractionVersion1.
 * This is really just a container to hand off more complex interactions to the UI when asked for.
 */
public class TestSingleDualPaneUIInteractionVersion1 implements SinglePaneUIInteractionVersion1, DualPaneUIInteractionVersion1 {

    private AlternateUIInteractionVersion1 alternateUIInteractionVersion1;
    private SettingsNodeVersion1 settingsNodeVersion1;

    public TestSingleDualPaneUIInteractionVersion1(AlternateUIInteractionVersion1 alternateUIInteractionVersion1, SettingsNodeVersion1 settingsNodeVersion1) {
        this.alternateUIInteractionVersion1 = alternateUIInteractionVersion1;
        this.settingsNodeVersion1 = settingsNodeVersion1;
    }

    public AlternateUIInteractionVersion1 instantiateAlternateUIInteraction() {
        return alternateUIInteractionVersion1;
    }

    public SettingsNodeVersion1 instantiateSettings() {
        return settingsNodeVersion1;
    }
}
