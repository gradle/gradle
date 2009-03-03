package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.groovy.scripts.ScriptSource;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class EmbeddedScriptSettingsFinderTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ISettingsFinder delegate = context.mock(ISettingsFinder.class);
    private final ScriptSource settingsScriptSource = context.mock(ScriptSource.class);
    private final EmbeddedScriptSettingsFinder settingsFinder = new EmbeddedScriptSettingsFinder(delegate);

    @Test
    public void usesProvidedScriptAsSettingsFileWhenSettingsFileSpecifiedInStartParam() {
        StartParameter parameter = new StartParameter();
        parameter.setSettingsScriptSource(settingsScriptSource);

        settingsFinder.find(parameter);

        assertThat(settingsFinder.getSettingsScriptSource(), sameInstance(settingsScriptSource));
    }

    @Test
    public void usesCurrentDirAsSettingsDirWhenSettingsFileSpecifiedInStartParam() throws IOException {
        StartParameter parameter = new StartParameter();
        File currentDir = new File("current dir");

        parameter.setSettingsScriptSource(settingsScriptSource);
        parameter.setCurrentDir(currentDir);

        settingsFinder.find(parameter);

        assertThat(settingsFinder.getSettingsDir(), equalTo(currentDir.getCanonicalFile()));
    }

    @Test
    public void delegatesWhenSettingsFileNotSpecifiedInStartParam() {
        final StartParameter parameter = new StartParameter();
        final File settingsDir = new File("settings dir");

        context.checking(new Expectations() {{
            one(delegate).find(parameter);
            one(delegate).getSettingsDir();
            will(returnValue(settingsDir));
            one(delegate).getSettingsScriptSource();
            will(returnValue(settingsScriptSource));
        }});

        settingsFinder.find(parameter);

        assertThat(settingsFinder.getSettingsDir(), sameInstance(settingsDir));
        assertThat(settingsFinder.getSettingsScriptSource(), sameInstance(settingsScriptSource));
    }
}
