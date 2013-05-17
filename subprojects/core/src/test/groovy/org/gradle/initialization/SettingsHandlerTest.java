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
package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.initialization.buildsrc.BuildSourceBuilder;
import org.gradle.invocation.BuildClassLoaderRegistry;
import org.gradle.util.GFileUtils;
import org.gradle.util.WrapUtil;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
public class SettingsHandlerTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private GradleInternal gradle = context.mock(GradleInternal.class);
    private SettingsInternal settings = context.mock(SettingsInternal.class);
    private SettingsLocation settingsLocation = new SettingsLocation(GFileUtils.canonicalise(new File("someDir")), null);
    private StartParameter startParameter = new StartParameter();
    private URLClassLoader urlClassLoader = new URLClassLoader(new URL[0]);
    private ISettingsFinder settingsFinder = context.mock(ISettingsFinder.class);
    private SettingsProcessor settingsProcessor = context.mock(SettingsProcessor.class);
    private BuildSourceBuilder buildSourceBuilder = context.mock(BuildSourceBuilder.class);
    private SettingsHandler settingsHandler = new SettingsHandler(settingsFinder, settingsProcessor,
            buildSourceBuilder);

    @org.junit.Test
    public void findAndLoadSettingsWithExistingSettings() {
        prepareForExistingSettings();
        context.checking(new Expectations() {{
            allowing(buildSourceBuilder).buildAndCreateClassLoader(with(aBuildSrcStartParameter(new File(
                    settingsLocation.getSettingsDir(), BaseSettings.DEFAULT_BUILD_SRC_DIR))));
            will(returnValue(urlClassLoader));
        }});
        assertThat(settingsHandler.findAndLoadSettings(gradle), sameInstance(settings));
    }

    private void prepareForExistingSettings() {
        final ProjectRegistry projectRegistry = context.mock(ProjectRegistry.class);
        final DefaultProjectDescriptor projectDescriptor = context.mock(DefaultProjectDescriptor.class);
        final ServiceRegistryFactory services = context.mock(ServiceRegistryFactory.class);
        final BuildClassLoaderRegistry classLoaderRegistry = context.mock(BuildClassLoaderRegistry.class);
        startParameter.setCurrentDir(settingsLocation.getSettingsDir());

        context.checking(new Expectations() {{
            allowing(settings).getProjectRegistry();
            will(returnValue(projectRegistry));

            allowing(projectRegistry).getAllProjects();
            will(returnValue(WrapUtil.toSet(projectDescriptor)));

            allowing(projectDescriptor).getProjectDir();
            will(returnValue(settingsLocation.getSettingsDir()));

            allowing(projectDescriptor).getBuildFile();
            will(returnValue(new File(settingsLocation.getSettingsDir(), "build.gradle")));

            allowing(settings).getClassLoader();
            will(returnValue(urlClassLoader));

            allowing(services).get(BuildClassLoaderRegistry.class);
            will(returnValue(classLoaderRegistry));

            allowing(gradle).getStartParameter();
            will(returnValue(startParameter));

            allowing(gradle).getServices();
            will(returnValue(services));

            allowing(settingsFinder).find(startParameter);
            will(returnValue(settingsLocation));

            one(settingsProcessor).process(gradle, settingsLocation, urlClassLoader, startParameter);
            will(returnValue(settings));

            one(classLoaderRegistry).addRootClassLoader(urlClassLoader);
        }});
    }

    @Factory
    public static Matcher<StartParameter> aBuildSrcStartParameter(File currentDir) {
        return new BuildSrcParameterMatcher(currentDir);
    }

    public static class BuildSrcParameterMatcher extends TypeSafeMatcher<StartParameter> {
        private File currentDir;

        public BuildSrcParameterMatcher(File currentDir) {
            this.currentDir = currentDir;
        }

        public boolean matchesSafely(StartParameter startParameter) {
            try {
                return startParameter.getCurrentDir().getCanonicalFile().equals(currentDir.getCanonicalFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void describeTo(Description description) {
            description.appendText("a startparameter with ").appendValue(currentDir);
        }
    }
}
