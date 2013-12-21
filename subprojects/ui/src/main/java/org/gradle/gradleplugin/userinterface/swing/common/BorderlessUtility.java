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
package org.gradle.gradleplugin.userinterface.swing.common;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Utility functions/constants for borderless buttons
 */
public class BorderlessUtility {
    public static final Color ON_MOUSE_OVER_BACKGROUND = new Color(181, 190, 214);
    public static final Color ON_BUTTON_PRESSED_BACKGROUND = new Color(130, 146, 185);
    public static final Border ON_MOUSEOVER_BORDER = BorderFactory.createLineBorder(new Color(8, 36, 107));

    //make the default border NOT an EMPTY border (so things don't resize), but make it appear clear by using the panel background color
    public static final Border DEFAULT_BORDER = BorderFactory.createLineBorder(UIManager.getColor("Panel.background"));
}
