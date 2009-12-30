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
package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.specs.Spec;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestClosure;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultDomainObjectContainerTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final DefaultDomainObjectContainer<CharSequence> container = new DefaultDomainObjectContainer<CharSequence>(CharSequence.class);

    @Test
    public void canGetAllDomainObjectsForEmptyCollection() {
        assertTrue(container.getAll().isEmpty());
    }

    @Test
    public void canGetAllDomainObjectsOrderedByOrderAdded() {
        container.addObject("b");
        container.addObject("a");
        container.addObject("c");

        assertThat(container.getAll(), equalTo(toLinkedSet((CharSequence) "b", "a", "c")));
    }

    @Test
    public void canIterateOverEmptyCollection() {
        Iterator<CharSequence> iterator = container.iterator();
        assertFalse(iterator.hasNext());
    }

    @Test
    public void canIterateOverDomainObjectsOrderedByOrderAdded() {
        container.addObject("b");
        container.addObject("a");
        container.addObject("c");

        Iterator<CharSequence> iterator = container.iterator();
        assertThat(iterator.next(), equalTo((CharSequence) "b"));
        assertThat(iterator.next(), equalTo((CharSequence) "a"));
        assertThat(iterator.next(), equalTo((CharSequence) "c"));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void canGetAllMatchingDomainObjectsOrderedByOrderAdded() {
        Spec<CharSequence> spec = new Spec<CharSequence>() {
            public boolean isSatisfiedBy(CharSequence element) {
                return !element.equals("b");
            }
        };

        container.addObject("a");
        container.addObject("b");
        container.addObject("c");

        assertThat(container.findAll(spec), equalTo(toLinkedSet((CharSequence) "a", "c")));
    }

    @Test
    public void getAllMatchingDomainObjectsReturnsEmptySetWhenNoMatches() {
        Spec<CharSequence> spec = new Spec<CharSequence>() {
            public boolean isSatisfiedBy(CharSequence element) {
                return false;
            }
        };

        container.addObject("a");

        assertTrue(container.findAll(spec).isEmpty());
    }

    @Test
    public void canGetFilteredCollectionContainingAllObjectsWhichMeetSpec() {
        Spec<CharSequence> spec = new Spec<CharSequence>() {
            public boolean isSatisfiedBy(CharSequence element) {
                return !element.equals("b");
            }
        };

        container.addObject("a");
        container.addObject("b");
        container.addObject("c");

        assertThat(container.matching(spec).getAll(), equalTo(toLinkedSet((CharSequence) "a", "c")));
    }

    @Test
    public void canGetFilteredCollectionContainingAllObjectsWhichHaveType() {
        container.addObject("c");
        container.addObject("a");
        container.addObject(new StringBuffer("b"));

        assertThat(container.withType(CharSequence.class).getAll(), equalTo(container.getAll()));
        assertThat(container.withType(String.class).getAll(), equalTo(toLinkedSet("c", "a")));
    }

    @Test
    public void filteredCollectionIsLive() {
        Spec<CharSequence> spec = new Spec<CharSequence>() {
            public boolean isSatisfiedBy(CharSequence element) {
                return !element.equals("a");
            }
        };

        container.addObject("a");

        DomainObjectCollection<CharSequence> filteredCollection = container.matching(spec);
        assertTrue(filteredCollection.getAll().isEmpty());

        container.addObject("b");
        container.addObject("c");

        assertThat(filteredCollection.getAll(), equalTo(toLinkedSet((CharSequence) "b", "c")));
    }

    @Test
    public void filteredCollectionExecutesActionWhenMatchingObjectAdded() {
        final Action<CharSequence> action = context.mock(Action.class);

        context.checking(new Expectations() {{
            one(action).execute("a");
        }});

        Spec<CharSequence> spec = new Spec<CharSequence>() {
            public boolean isSatisfiedBy(CharSequence element) {
                return !element.equals("b");
            }
        };

        container.matching(spec).whenObjectAdded(action);

        container.addObject("a");
        container.addObject("b");
    }

    @Test
    public void filteredCollectionExecutesClosureWhenMatchingObjectAdded() {
        final TestClosure closure = context.mock(TestClosure.class);

        context.checking(new Expectations() {{
            one(closure).call("a");
        }});

        Spec<CharSequence> spec = new Spec<CharSequence>() {
            public boolean isSatisfiedBy(CharSequence element) {
                return !element.equals("b");
            }
        };

        container.matching(spec).whenObjectAdded(HelperUtil.toClosure(closure));

        container.addObject("a");
        container.addObject("b");
    }

    @Test
    public void canChainFilteredCollections() {
        Spec<CharSequence> spec = new Spec<CharSequence>() {
            public boolean isSatisfiedBy(CharSequence element) {
                return !element.equals("b");
            }
        };
        Spec<String> spec2 = new Spec<String>() {
            public boolean isSatisfiedBy(String element) {
                return !element.equals("c");
            }
        };

        container.addObject("a");
        container.addObject("b");
        container.addObject("c");
        container.addObject(new StringBuffer("d"));

        DomainObjectCollection<String> collection = container.matching(spec).withType(String.class).matching(spec2);
        assertThat(collection.getAll(), equalTo(toSet("a")));
    }

    @Test
    public void callsActionWhenObjectAdded() {
        final Action<CharSequence> action = context.mock(Action.class);

        context.checking(new Expectations() {{
            one(action).execute("a");
        }});

        container.whenObjectAdded(action);
        container.addObject("a");
    }

    @Test
    public void callsClosureWhenObjectAdded() {
        final TestClosure closure = context.mock(TestClosure.class);

        context.checking(new Expectations() {{
            one(closure).call("a");
        }});

        container.whenObjectAdded(HelperUtil.toClosure(closure));
        container.addObject("a");
    }

    @Test
    public void callsActionWhenObjectRemoved() {
        final Action<CharSequence> action = context.mock(Action.class);
        final String original = "a";

        context.checking(new Expectations() {{
            one(action).execute(with(sameInstance(original)));
        }});

        container.whenObjectRemoved(action);
        container.addObject(original);
        container.addObject("a");
    }

    @Test
    public void allObjectsCallsActionForEachExistingObject() {
        final Action<CharSequence> action = context.mock(Action.class);

        context.checking(new Expectations() {{
            one(action).execute("a");
        }});

        container.addObject("a");
        container.allObjects(action);
    }

    @Test
    public void allObjectsCallsClosureForEachExistingObject() {
        final TestClosure closure = context.mock(TestClosure.class);

        context.checking(new Expectations() {{
            one(closure).call("a");
        }});

        container.addObject("a");
        container.allObjects(HelperUtil.toClosure(closure));
    }

    @Test
    public void allObjectsCallsActionForEachNewObject() {
        final Action<CharSequence> action = context.mock(Action.class);

        context.checking(new Expectations() {{
            one(action).execute("a");
        }});

        container.allObjects(action);
        container.addObject("a");
    }

    @Test
    public void allObjectsCallsClosureForEachNewObject() {
        final TestClosure closure = context.mock(TestClosure.class);

        context.checking(new Expectations() {{
            one(closure).call("a");
        }});

        container.allObjects(HelperUtil.toClosure(closure));
        container.addObject("a");
    }

}
