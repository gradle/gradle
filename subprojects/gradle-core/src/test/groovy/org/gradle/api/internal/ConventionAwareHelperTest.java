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
import org.gradle.util.TestTask;
import org.gradle.util.WrapUtil;
import org.gradle.util.HelperUtil;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

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

    @Test public void testConventionMapping() {
        final List expectedList1 = WrapUtil.toList("a");
        final List expectedList2 = WrapUtil.toList("b");
        assertSame(conventionAware, conventionAware.map(WrapUtil.<String, ConventionValue>toMap("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return expectedList1;
            }
        })));
        assertSame(conventionAware, conventionAware.map(WrapUtil.<String, ConventionValue>toMap("list2", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return expectedList2;
            }
        })));
        assertSame(expectedList1, conventionAware.getConventionValue("list1"));
        
        assertSame(expectedList2, conventionAware.getConventionValue("list2"));
    }

    @Test (expected = InvalidUserDataException.class) public void testIllegalMapping() {
        conventionAware.map(WrapUtil.<String, ConventionValue>toMap("unknownProp", null));
    }

    @Test public void testOverwriteProperties() {
        final List conventionList1 = WrapUtil.toList("a");
        conventionAware.map(WrapUtil.<String, ConventionValue>toMap("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return conventionList1;
            }
        }));
        assertSame(conventionList1, conventionAware.getConventionValue("list1"));
        List expectedList1 = WrapUtil.toList("b");
        testTask.setList1(expectedList1);
        assertSame(expectedList1, conventionAware.getConventionValue("list1"));
    }

    @Test public void testCachedProperties() {
        Object value = conventionAware.map(WrapUtil.<String, ConventionValue>toMap("list1", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return WrapUtil.toList("a");
            }
        }));
        assertSame(conventionAware.getConventionValue("list1"), conventionAware.getConventionValue("list1"));
    }
}
