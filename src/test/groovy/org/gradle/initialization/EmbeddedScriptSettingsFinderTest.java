package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.groovy.scripts.ScriptSource;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class EmbeddedScriptSettingsFinderTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final EmbeddedScriptSettingsFinder settingsFinder = new EmbeddedScriptSettingsFinder();

    @Test
    public void usesProvidedScriptAsSettingsFile() {
        StartParameter parameter = new StartParameter();
        ScriptSource settingsScriptSource = context.mock(ScriptSource.class);

        parameter.setSettingsScriptSource(settingsScriptSource);

        settingsFinder.find(parameter);

        assertThat(settingsFinder.getSettingsScriptSource(), sameInstance(settingsScriptSource));
    }

    @Test
    public void usesCurrentDirAsSettingsDir() throws IOException {
        StartParameter parameter = new StartParameter();
        File currentDir = new File("current dir");

        parameter.setCurrentDir(currentDir);

        settingsFinder.find(parameter);

        assertThat(settingsFinder.getSettingsDir(), equalTo(currentDir.getCanonicalFile()));
    }
}
