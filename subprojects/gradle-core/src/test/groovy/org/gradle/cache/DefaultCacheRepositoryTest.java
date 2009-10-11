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
package org.gradle.cache;

import org.gradle.CacheUsage;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.integtests.TestFile;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Map;

@RunWith(JMock.class)
public class DefaultCacheRepositoryTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TestFile homeDir = tmpDir.dir("home");
    private final TestFile buildRootDir = tmpDir.dir("build");
    private final TestFile sharedCacheDir = homeDir.file("caches");
    private final String version = new GradleVersion().getVersion();
    private final Map properties = GUtil.map("a", "value", "b", "value2");
    private final Map expectedPropreties = GUtil.addMaps(properties, GUtil.map("version", version));
    private final DefaultCacheRepository repository = new DefaultCacheRepository(homeDir, CacheUsage.ON);

    @Test
    public void createsGlobalCache() {
        PersistentCache cache = repository.getGlobalCache("a/b/c", properties);
        assertThat(cache, instanceOf(DefaultPersistentCache.class));

        DefaultPersistentCache dCache = (DefaultPersistentCache) cache;
        assertThat(dCache.getBaseDir(), equalTo((File) sharedCacheDir.file("a/b/c")));
        assertThat(dCache.getProperties(), equalTo(expectedPropreties));
    }

    @Test
    public void createsCacheForGradle() {
        final Gradle gradle = context.mock(Gradle.class);
        final Project project = context.mock(Project.class);
        context.checking(new Expectations() {{
            allowing(gradle).getRootProject();
            will(returnValue(project));
            allowing(project).getProjectDir();
            will(returnValue(buildRootDir));
        }});

        PersistentCache cache = repository.getCacheFor(gradle, "a/b/c", properties);
        assertThat(cache, instanceOf(DefaultPersistentCache.class));

        DefaultPersistentCache dCache = (DefaultPersistentCache) cache;
        assertThat(dCache.getBaseDir(), equalTo((File) buildRootDir.file(".gradle/a/b/c")));
        assertThat(dCache.getProperties(), equalTo(expectedPropreties));
    }

    @Test
    public void removesGradle0_8ScriptCache() {
        homeDir.file("scriptCache/subdir/some.file").touch();

        repository.getGlobalCache("a/b/c", properties);

        homeDir.file("scriptCache").assertDoesNotExist();
    }
}
