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
import org.gradle.util.TestFile;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.TemporaryFolder;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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
    private final CacheFactory cacheFactory = context.mock(CacheFactory.class);
    private final DefaultCacheRepository repository = new DefaultCacheRepository(homeDir, CacheUsage.ON, cacheFactory);

    @Test
    public void createsGlobalCache() {
        final PersistentCache cache = context.mock(PersistentCache.class);

        context.checking(new Expectations(){{
            one(cacheFactory).open(sharedCacheDir.file(version + "/a/b/c"), CacheUsage.ON, properties);
            will(returnValue(cache));
        }});

        assertThat(repository.getGlobalCache("a/b/c", properties), sameInstance(cache));
    }

    @Test
    public void createsCacheForGradle() {
        final Gradle gradle = context.mock(Gradle.class);
        final Project project = context.mock(Project.class);
        final PersistentCache cache = context.mock(PersistentCache.class);

        context.checking(new Expectations() {{
            allowing(gradle).getRootProject();
            will(returnValue(project));
            allowing(project).getProjectDir();
            will(returnValue(buildRootDir));
            one(cacheFactory).open(buildRootDir.file(".gradle/" + version + "/a/b/c"), CacheUsage.ON, properties);
            will(returnValue(cache));
        }});

        assertThat(repository.getCacheFor(gradle, "a/b/c", properties), sameInstance(cache));
    }
}
