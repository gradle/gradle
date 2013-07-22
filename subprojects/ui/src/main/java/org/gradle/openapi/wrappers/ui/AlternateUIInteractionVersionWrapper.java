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
package org.gradle.openapi.wrappers.ui;

import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;
import org.gradle.openapi.external.ui.AlternateUIInteractionVersion1;

import java.io.File;

/**
 * Wrapper to shield version changes in AlternateUIInteraction from an external user of gradle open API.
 */
public class AlternateUIInteractionVersionWrapper implements AlternateUIInteraction {
    private AlternateUIInteractionVersion1 alternateUIInteractionVersion1;
    private SettingsNode settings;

    public AlternateUIInteractionVersionWrapper(AlternateUIInteractionVersion1 alternateUIInteractionVersion1, SettingsNode settings) {
        this.alternateUIInteractionVersion1 = alternateUIInteractionVersion1;
        this.settings = settings;

        //when future versions are added, doing the following and then delegating
        //the new functions to AlternateUIInteractionVersion2 keeps things compatible.
        //try
        //{
        //   if( alternateUIInteractionVersion1 instanceof AlternateUIInteractionVersion2 )
        //      alternateUIInteractionVersion2 = (AlternateUIInteractionVersion2) alternateUIInteractionVersion1;
        //}
        //catch( NoClassDefFoundError e )
        //{
        //   //this just means that we're being run with an old version of the open API. We have no alternateUIInteractionVersion2
        //}
    }

    public void openFile(File file, int line) {
        alternateUIInteractionVersion1.openFile(file, line);
    }

    public void editFile(File file, int line) {
        alternateUIInteractionVersion1.editFile(file, line);
    }

    public boolean doesSupportEditingOpeningFiles() {
        return alternateUIInteractionVersion1.doesSupportEditingOpeningFiles();
    }

    public void aboutToExecuteCommand(String fullCommandLine) {
        alternateUIInteractionVersion1.aboutToExecuteCommand(fullCommandLine);
    }
}
