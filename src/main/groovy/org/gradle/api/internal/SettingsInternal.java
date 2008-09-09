package org.gradle.api.internal;

import org.gradle.StartParameter;
import org.gradle.api.initialization.Settings;

import java.net.URLClassLoader;

public interface SettingsInternal extends Settings {
    URLClassLoader createClassLoader();

    StartParameter getStartParameter();
}
