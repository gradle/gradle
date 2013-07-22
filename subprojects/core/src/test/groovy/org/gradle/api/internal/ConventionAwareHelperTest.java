/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestTask;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.gradle.util.WrapUtil.toList;
import static org.gradle.util.WrapUtil.toMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.*;

public class ConventionAwareHelperTest {
    ConventionAwareHelper conventionAware;

    TestTask testTask;

    @Before public void setUp() {
        testTask = HelperUtil.createTask(TestTask.class);
        conventionAware = new ConventionAwareHelper(testTask);
    }

    @Test
    public void canMapPropertiesUsingClosure() {
        conventionAware.map("list1", HelperUtil.toClosure("{ ['a'] }"));
        assertThat(conventionAware.getConventionValue(null, "list1", false), equalTo((Object) toList("a")));

        conventionAware.map("list1", HelperUtil.toClosure("{ convention -> [convention] }"));
        assertThat(conventionAware.getConventionValue(null, "list1", false), equalTo((Object) toList(conventionAware.getConvention())));

        conventionAware.map("list1", HelperUtil.toClosure("{ convention, object -> [convention, object] }"));
        assertThat(conventionAware.getConventionValue(null, "list1", false), equalTo((Object) toList(conventionAware.getConvention(), testTask)));
    }

    @Test
    public void canMapPropertiesUsingCallable() {
        Callable callable = new Callable() {
            public Object call() throws Exception {
                return toList("a");
            }
        };

        conventionAware.map("list1", callable);
        assertThat(conventionAware.getConventionValue(null, "list1", false), equalTo((Object) toList("a")));
    }
    
    @Test
    public void canSetMappingUsingDynamicProperty() {
        HelperUtil.call("{ it.list1 = { ['a'] } }", conventionAware);
        assertThat(conventionAware.getConventionValue(null, "list1", false), equalTo((Object) toList("a")));
    }
    
    @Test (expected = InvalidUserDataException.class) public void cannotMapUnknownProperty() {
        conventionAware.map("unknownProp", new Callable<Object>() {
            public Object call() throws Exception {
                throw new UnsupportedOperationException();
            }
        });
    }

    @Test public void canOverwriteProperties() {
        final List conventionList1 = toList("a");
        conventionAware.map("list1", new Callable<Object>() {
            public Object call() {
                return conventionList1;
            }
        });
        assertSame(conventionList1, conventionAware.getConventionValue(null, "list1", false));
        List expectedList1 = toList("b");
        assertSame(expectedList1, conventionAware.getConventionValue(expectedList1, "list1", true));
    }

    @Test public void canEnableCachingOfPropertyValue() {
        conventionAware.map("list1", new Callable<Object>() {
            public Object call() {
                return toList("a");
            }
        }).cache();
        assertSame(conventionAware.getConventionValue(null, "list1", false), conventionAware.getConventionValue(null, "list1", false));
    }

    @Test public void notCachesPropertyValuesByDefault() {
        conventionAware.map("list1", new Callable<Object>() {
            public Object call() {
                return toList("a");
            }
        });

        Object value1 = conventionAware.getConventionValue(null, "list1", false);
        Object value2 = conventionAware.getConventionValue(null, "list1", false);
        assertEquals(value1, value2);
        assertNotSame(value1, value2);
    }

    @Test public void doesNotUseMappingWhenExplicitValueProvided() {
        conventionAware.map("list1", new Callable<Object>() {
            public Object call() {
                throw new UnsupportedOperationException();
            }
        });

        List<Object> value = emptyList();
        assertThat(conventionAware.getConventionValue(value, "list1", true), sameInstance(value));
    }

    @Test public void usesConventionValueForEmptyCollection() {
        conventionAware.map("list1", new Callable<Object>() {
            public Object call() {
                return toList("a");
            }
        });
        assertThat(conventionAware.getConventionValue(emptyList(), "list1", false), equalTo((Object) toList("a")));
    }

    @Test public void usesConventionValueForEmptyMap() {
        conventionAware.map("map1", new Callable<Object>() {
            public Object call() {
                return toMap("a", "b");
            }
        });
        assertThat(conventionAware.getConventionValue(emptyMap(), "map1", false), equalTo((Object) toMap("a", "b")));
    }
}
