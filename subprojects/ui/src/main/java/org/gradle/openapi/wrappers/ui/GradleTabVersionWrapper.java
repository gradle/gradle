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

import org.gradle.gradleplugin.userinterface.swing.generic.tabs.GradleTab;
import org.gradle.openapi.external.ui.GradleTabVersion1;

import java.awt.*;

/**
 * Wrapper to shield version changes in GradleTab from an external user of the gradle open API.
 */
public class GradleTabVersionWrapper implements GradleTab {
    private GradleTabVersion1 gradleTabVersion1;

    GradleTabVersionWrapper(GradleTabVersion1 gradleTabVersion1) {
        this.gradleTabVersion1 = gradleTabVersion1;

        //when future versions are added, doing the following and then delegating
        //the new functions to GradleTabVersion2 keeps things compatible.
        //if( gradleTabVersion1 instanceof GradleTabVersion2 )
        //   gradleTabVersion2 = (GradleTabVersion2) gradleTabVersion1;
    }

    public String getName() {
        return gradleTabVersion1.getName();
    }

    public Component createComponent() {
        return gradleTabVersion1.createComponent();
    }

    public void aboutToShow() {
        gradleTabVersion1.aboutToShow();
    }
}
