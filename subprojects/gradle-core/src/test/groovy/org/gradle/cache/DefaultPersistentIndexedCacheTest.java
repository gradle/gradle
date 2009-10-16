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

import org.gradle.util.TemporaryFolder;
import org.gradle.integtests.TestFile;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultPersistentIndexedCacheTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final PersistentCache backingCache = context.mock(PersistentCache.class);
    private final DefaultPersistentIndexedCache<String, Integer> cache
            = new DefaultPersistentIndexedCache<String, Integer>(backingCache);

    @Before
    public void setup() {
        context.checking(new Expectations(){{
            allowing(backingCache).getBaseDir();
            will(returnValue(tmpDir.getDir()));
        }});
    }
    
    @Test
    public void getReturnsNullWhenEntryDoesNotExist() {

        assertNull(cache.get("unknown"));
    }

    @Test
    public void savesCacheFileWhenEntryAdded() {
        expectCacheUpdated();

        cache.put("key_1", 2);

        assertThat(cache.get("key_1"), equalTo(2));
    }

    @Test
    public void savesCacheFileWhenEntryRemoved() {
        expectCacheUpdated();

        cache.put("key_1", 2);
        assertThat(cache.get("key_1"), equalTo(2));

        cache.put("otherkey", 3);
        
        cache.remove("key_1");

        assertThat(cache.get("key_1"), nullValue());
        assertThat(cache.get("otherkey"), equalTo(3));
    }

    @Test
    public void handlesBadlyFormedCacheFile() {
        expectCacheUpdated();

        cache.put("key_1", 2);

        TestFile testFile = tmpDir.getDir().file("ke/key_/key_1_.bin");
        testFile.assertIsFile();
        testFile.write("some junk");

        assertNull(cache.get("key_1"));
    }

    @Test
    public void escapesKeyNames() {
        expectCacheUpdated();

        cache.put("a/b/c/d/e", 2);
        cache.put("a\\b\\c\\d\\e", 3);
        cache.put("a_b_c_d_e", 4);
        cache.put(".", 5);
        cache.put("/abcd", 6);
        cache.put("q:\\abcd", 7);

        assertThat(cache.get("a/b/c/d/e"), equalTo(2));
        assertThat(cache.get("a\\b\\c\\d\\e"), equalTo(3));
        assertThat(cache.get("a_b_c_d_e"), equalTo(4));
        assertThat(cache.get("."), equalTo(5));
        assertThat(cache.get("/abcd"), equalTo(6));
        assertThat(cache.get("q:\\abcd"), equalTo(7));
    }

    @Test
    public void handlesShortKeyNames() {
        expectCacheUpdated();

        cache.put(null, 6);
        cache.put("", 7);
        cache.put("1", 8);
        cache.put("12", 9);
        cache.put("1234", 10);
        cache.put("12345", 11);

        assertThat(cache.get(null), equalTo(6));
        assertThat(cache.get(""), equalTo(7));
        assertThat(cache.get("1"), equalTo(8));
        assertThat(cache.get("12"), equalTo(9));
        assertThat(cache.get("1234"), equalTo(10));
        assertThat(cache.get("12345"), equalTo(11));
    }

    private void expectCacheUpdated() {
        context.checking(new Expectations() {{
            atLeast(1).of(backingCache).update();
        }});
    }

}
