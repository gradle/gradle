/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.extensibility;

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.TestTask;
import org.gradle.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.gradle.util.internal.WrapUtil.toList;
import static org.gradle.util.internal.WrapUtil.toMap;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ConventionAwareHelperTest {
    ConventionAwareHelper conventionAware;

    TestTask testTask;

    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass());

    @Before
    public void setUp() {
        testTask = TestUtil.create(temporaryFolder).task(TestTask.class);
        conventionAware = new ConventionAwareHelper(testTask);
    }

    @Test
    public void canMapPropertiesUsingClosure() {
        conventionAware.map("list1", TestUtil.toClosure("{ ['a'] }"));
        assertThat(conventionAware.getConventionValue(null, "list1", false), equalTo((Object) toList("a")));
    }

    @Test
    public void canMapPropertiesUsingCallable() {
        Callable callable = () -> toList("a");

        conventionAware.map("list1", callable);
        assertThat(conventionAware.getConventionValue(null, "list1", false), equalTo((Object) toList("a")));
    }

    @Test
    public void canSetMappingUsingDynamicProperty() {
        TestUtil.call("{ it.list1 = { ['a'] } }", conventionAware);
        assertThat(conventionAware.getConventionValue(null, "list1", false), equalTo((Object) toList("a")));
    }

    @Test(expected = InvalidUserDataException.class)
    public void cannotMapUnknownProperty() {
        conventionAware.map("unknownProp", () -> {
            throw new UnsupportedOperationException();
        });
    }

    @Test
    public void canOverwriteProperties() {
        final List conventionList1 = toList("a");
        conventionAware.map("list1", () -> conventionList1);
        assertSame(conventionList1, conventionAware.getConventionValue(null, "list1", false));
        List expectedList1 = toList("b");
        assertSame(expectedList1, conventionAware.getConventionValue(expectedList1, "list1", true));
    }

    @Test
    public void canEnableCachingOfPropertyValue() {
        conventionAware.map("list1", () -> toList("a")).cache();
        assertSame(conventionAware.getConventionValue(null, "list1", false), conventionAware.getConventionValue(null, "list1", false));
    }

    @Test
    public void notCachesPropertyValuesByDefault() {
        conventionAware.map("list1", () -> toList("a"));

        Object value1 = conventionAware.getConventionValue(null, "list1", false);
        Object value2 = conventionAware.getConventionValue(null, "list1", false);
        assertEquals(value1, value2);
        assertNotSame(value1, value2);
    }

    @Test
    public void doesNotUseMappingWhenExplicitValueProvided() {
        conventionAware.map("list1", () -> {
            throw new UnsupportedOperationException();
        });

        List<Object> value = emptyList();
        assertThat(conventionAware.getConventionValue(value, "list1", true), sameInstance(value));
    }

    @Test
    public void usesConventionValueForEmptyCollection() {
        conventionAware.map("list1", () -> toList("a"));
        assertThat(conventionAware.getConventionValue(emptyList(), "list1", false), equalTo((Object) toList("a")));
    }

    @Test
    public void usesConventionValueForEmptyMap() {
        conventionAware.map("map1", () -> toMap("a", "b"));
        assertThat(conventionAware.getConventionValue(emptyMap(), "map1", false), equalTo((Object) toMap("a", "b")));
    }

    @Test
    public void mapsPropertyBackedByLazyPropertyToItsConvention() {
        LazyBackedTask task = TestUtil.create(temporaryFolder).task(LazyBackedTask.class);
        ConventionAwareHelper helper = new ConventionAwareHelper((IConventionAware) task);
        String[] value = {"first"};

        helper.map("message", () -> value[0]);
        value[0] = "second";

        assertEquals("second", task.getMessageProperty().get());
        assertEquals("second", task.getMessage());
        // The mapping is not kept by the helper, so the generated getter returns the actual value
        assertEquals("actual", helper.getConventionValue("actual", "message", false));
    }

    @Test
    public void explicitValueWinsOverMappedConvention() {
        LazyBackedTask task = TestUtil.create(temporaryFolder).task(LazyBackedTask.class);
        ConventionAwareHelper helper = new ConventionAwareHelper((IConventionAware) task);

        helper.map("message", () -> "convention");
        task.getMessageProperty().set("explicit");
        assertEquals("explicit", task.getMessage());

        task.setMessage(null);
        assertEquals(null, task.getMessage());

        task.getMessageProperty().unset();
        assertEquals("convention", task.getMessage());
    }

    @Test
    public void mappingOfPropertyBackedByReadOnlyProviderFails() {
        LazyBackedTask task = TestUtil.create(temporaryFolder).task(LazyBackedTask.class);
        ConventionAwareHelper helper = new ConventionAwareHelper((IConventionAware) task);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> helper.map("readOnly", () -> "value"));
        assertThat(e.getMessage(), startsWith("Cannot map property " + LazyBackedTask.class.getName() + ".readOnly: the lazy property of type "));
    }

    @Test
    public void mappingOfPropertyWithWrongPathFails() {
        LazyBackedTask task = TestUtil.create(temporaryFolder).task(LazyBackedTask.class);
        ConventionAwareHelper helper = new ConventionAwareHelper((IConventionAware) task);

        InvalidUserCodeException e = assertThrows(InvalidUserCodeException.class, () -> helper.map("broken", () -> "value"));
        assertThat(e.getMessage(), startsWith("Cannot resolve @BackedByLazyProperty(\"missing\") of " + LazyBackedTask.class.getName() + ".getBroken(): "));
    }

    public static abstract class LazyBackedTask extends DefaultTask {
        private final Property<String> message = getProject().getObjects().property(String.class);

        @Internal
        public Property<String> getMessageProperty() {
            return message;
        }

        @Internal
        @BackedByLazyProperty("messageProperty")
        public String getMessage() {
            return message.getOrNull();
        }

        public void setMessage(@Nullable String value) {
            EagerPropertySupport.setEager(message, value);
        }

        @Internal
        public Provider<String> getReadOnlyProvider() {
            return message.map(value -> value);
        }

        @Internal
        @BackedByLazyProperty("readOnlyProvider")
        public String getReadOnly() {
            return message.getOrNull();
        }

        public void setReadOnly(String value) {
        }

        @Internal
        @BackedByLazyProperty("missing")
        public String getBroken() {
            return null;
        }

        public void setBroken(String value) {
        }
    }
}
