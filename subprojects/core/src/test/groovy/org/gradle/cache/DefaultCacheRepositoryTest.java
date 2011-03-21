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
package org.gradle.cache;

import org.gradle.CacheUsage;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultCacheRepositoryTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TestFile homeDir = tmpDir.createDir("home");
    private final TestFile buildRootDir = tmpDir.createDir("build");
    private final TestFile sharedCacheDir = homeDir.file("caches");
    private final String version = GradleVersion.current().getVersion();
    private final Map<String, ?> properties = GUtil.map("a", "value", "b", "value2");
    private final CacheFactory cacheFactory = context.mock(CacheFactory.class);
    private final PersistentCache cache = context.mock(PersistentCache.class);
    private final Gradle gradle = context.mock(Gradle.class);
    private final DefaultCacheRepository repository = new DefaultCacheRepository(homeDir, CacheUsage.ON, cacheFactory);

    @Before
    public void setup() {
        context.checking(new Expectations() {{
            Project project = context.mock(Project.class);

            allowing(cache).getBaseDir();
            will(returnValue(tmpDir.getDir()));
            allowing(gradle).getRootProject();
            will(returnValue(project));
            allowing(project).getProjectDir();
            will(returnValue(buildRootDir));
        }});
    }

    @Test
    public void createsGlobalCache() {
        context.checking(new Expectations() {{
            one(cacheFactory).open(sharedCacheDir.file(version, "a/b/c"), CacheUsage.ON, Collections.EMPTY_MAP);
            will(returnValue(cache));
        }});

        assertThat(repository.cache("a/b/c").open(), sameInstance(cache));
    }

    @Test
    public void createsGlobalCacheWithProperties() {
        context.checking(new Expectations() {{
            one(cacheFactory).open(sharedCacheDir.file(version, "a/b/c"), CacheUsage.ON, properties);
            will(returnValue(cache));
        }});

        assertThat(repository.cache("a/b/c").withProperties(properties).open(), sameInstance(cache));
    }

    @Test
    public void createsCacheForAGradleInstance() {

        context.checking(new Expectations() {{
            one(cacheFactory).open(buildRootDir.file(".gradle", version, "a/b/c"), CacheUsage.ON,
                    Collections.EMPTY_MAP);
            will(returnValue(cache));
        }});

        assertThat(repository.cache("a/b/c").forObject(gradle).open(), sameInstance(cache));
    }

    @Test
    public void createsCacheForAFile() {
        final TestFile dir = tmpDir.createDir("otherDir");

        context.checking(new Expectations() {{
            one(cacheFactory).open(dir.file(".gradle", version, "a/b/c"), CacheUsage.ON, Collections.EMPTY_MAP);
            will(returnValue(cache));
        }});

        assertThat(repository.cache("a/b/c").forObject(dir).open(), sameInstance(cache));
    }

    @Test
    public void createsCrossVersionCache() {
        context.checking(new Expectations() {{
            one(cacheFactory).open(sharedCacheDir.file("noVersion", "a/b/c"), CacheUsage.ON, Collections.singletonMap(
                    "gradle.version", version));
            will(returnValue(cache));
        }});

        assertThat(repository.cache("a/b/c").invalidateOnVersionChange().open(), sameInstance(cache));
    }

    @Test
    public void createsCrossVersionCacheForAGradleInstance() {
        context.checking(new Expectations() {{
            one(cacheFactory).open(buildRootDir.file(".gradle", "noVersion", "a/b/c"), CacheUsage.ON,
                    Collections.singletonMap("gradle.version", version));
            will(returnValue(cache));
        }});

        assertThat(repository.cache("a/b/c").invalidateOnVersionChange().forObject(gradle).open(), sameInstance(cache));
    }
}
