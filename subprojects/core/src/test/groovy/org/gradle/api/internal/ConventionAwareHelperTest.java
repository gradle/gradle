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
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestTask;
import org.gradle.util.WrapUtil;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import static java.util.Collections.*;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Hans Dockter
 */
public class ConventionAwareHelperTest {
    ConventionAwareHelper conventionAware;

    TestTask testTask;

    @Before public void setUp() {
        testTask = HelperUtil.createTask(TestTask.class);
        conventionAware = new ConventionAwareHelper(testTask, new DefaultConvention());
    }

    @Test public void canMapPropertiesUsingConventionValue() {
        final List expectedList1 = toList("a");
        final List expectedList2 = toList("b");
        assertNotNull(conventionAware.map("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                assertSame(conventionAware.getConvention(), convention);
                assertSame(testTask, conventionAwareObject);
                return expectedList1;
            }
        }));
        assertNotNull(conventionAware.map("list2", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return expectedList2;
            }
        }));

        assertSame(expectedList1, conventionAware.getConventionValue("list1"));
        assertSame(expectedList2, conventionAware.getConventionValue("list2"));
    }

    @Test
    public void canMapPropertiesUsingClosure() {
        conventionAware.map("list1", HelperUtil.toClosure("{ ['a'] }"));
        assertThat(conventionAware.getConventionValue("list1"), equalTo((Object) toList("a")));

        conventionAware.map("list1", HelperUtil.toClosure("{ convention -> [convention] }"));
        assertThat(conventionAware.getConventionValue("list1"), equalTo((Object) toList(conventionAware.getConvention())));

        conventionAware.map("list1", HelperUtil.toClosure("{ convention, object -> [convention, object] }"));
        assertThat(conventionAware.getConventionValue("list1"), equalTo((Object) toList(conventionAware.getConvention(), testTask)));
    }

    @Test
    public void canMapPropertiesUsingCallable() {
        Callable callable = new Callable() {
            public Object call() throws Exception {
                return toList("a");
            }
        };

        conventionAware.map("list1", callable);
        assertThat(conventionAware.getConventionValue("list1"), equalTo((Object) toList("a")));
    }
    
    @Test
    public void canSetMappingUsingDynamicProperty() {
        HelperUtil.call("{ it.list1 = { ['a'] } }", conventionAware);
        assertThat(conventionAware.getConventionValue("list1"), equalTo((Object) toList("a")));
    }
    
    @Test (expected = InvalidUserDataException.class) public void cannotMapUnknownProperty() {
        conventionAware.map(WrapUtil.<String, ConventionValue>toMap("unknownProp", null));
    }

    @Test public void canOverwriteProperties() {
        final List conventionList1 = toList("a");
        conventionAware.map("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return conventionList1;
            }
        });
        assertSame(conventionList1, conventionAware.getConventionValue("list1"));
        List expectedList1 = toList("b");
        testTask.setList1(expectedList1);
        assertSame(expectedList1, conventionAware.getConventionValue("list1"));
    }

    @Test public void canEnableCachingOfPropertyValue() {
        conventionAware.map("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return toList("a");
            }
        }).cache();
        assertSame(conventionAware.getConventionValue("list1"), conventionAware.getConventionValue("list1"));
    }

    @Test public void notCachesPropertyValuesByDefault() {
        ConventionMapping.MappedProperty property = conventionAware.map("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return toList("a");
            }
        });

        Object value1 = conventionAware.getConventionValue("list1");
        Object value2 = conventionAware.getConventionValue("list1");
        assertEquals(value1, value2);
        assertNotSame(value1, value2);
    }

    @Test public void doesNotUseMappingWhenExplicitValueProvided() {
        conventionAware.map("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                throw new UnsupportedOperationException();
            }
        });

        List<Object> value = toList();
        assertThat(conventionAware.getConventionValue(value, "list1", true), sameInstance(value));
    }
    
    @Test public void usesConventionValueForEmptyCollection() {
        conventionAware.map("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return toList("a");
            }
        });
        assertThat(conventionAware.getConventionValue(toList(), "list1"), equalTo((Object) toList("a")));
    }

    @Test public void usesConventionValueForEmptyMap() {
        conventionAware.map("map1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return toMap("a", "b");
            }
        });
        assertThat(conventionAware.getConventionValue(emptyMap(), "map1"), equalTo((Object) toMap("a", "b")));
    }
}
