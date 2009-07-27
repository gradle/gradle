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
package org.gradle.api.internal.file;

import groovy.lang.Closure;
import org.gradle.api.file.SourceSet;
import org.gradle.api.tasks.StopActionException;
import org.gradle.util.HelperUtil;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class CompositeSourceSetTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final SourceSet set1 = context.mock(SourceSet.class, "set1");
    private final SourceSet set2 = context.mock(SourceSet.class, "set2");
    private final CompositeSourceSet set = new CompositeSourceSet("<display name>", set1, set2);

    @Test
    public void usesDisplayNameAsToString() {
        assertThat(set.toString(), equalTo("<display name>"));
    }

    @Test
    public void stopActionThrowsExceptionWhenSetIsEmpty() {
        CompositeSourceSet set = new CompositeSourceSet("<display name>");
        try {
            set.stopActionIfEmpty();
            fail();
        } catch (StopActionException e) {
            assertThat(e.getMessage(), equalTo("No source files found in <display name>."));
        }
    }

    @Test
    public void stopActionThrowsExceptionWhenAllSetsAreEmpty() {
        context.checking(new Expectations() {{
            one(set1).stopActionIfEmpty();
            will(throwException(new StopActionException()));
            one(set2).stopActionIfEmpty();
            will(throwException(new StopActionException()));
        }});

        try {
            set.stopActionIfEmpty();
            fail();
        } catch (StopActionException e) {
            assertThat(e.getMessage(), equalTo("No source files found in <display name>."));
        }
    }

    @Test
    public void stopActionDoesNotThrowsExceptionWhenSomeSetsAreNoEmpty() {
        context.checking(new Expectations() {{
            one(set1).stopActionIfEmpty();
            will(throwException(new StopActionException()));
            one(set2).stopActionIfEmpty();
        }});

        set.stopActionIfEmpty();
    }

    @Test
    public void addToAntBuilderDelegatesToEachSet() {
        context.checking(new Expectations(){{
            one(set1).addToAntBuilder("node", "name");
            one(set2).addToAntBuilder("node", "name");
        }});
        set.addToAntBuilder("node", "name");
    }
    
    @Test
    public void matchingReturnsUnionOfFilteredSets() {
        final Closure closure = HelperUtil.TEST_CLOSURE;
        final SourceSet filtered1 = context.mock(SourceSet.class, "filtered1");
        final SourceSet filtered2 = context.mock(SourceSet.class, "filtered2");

        context.checking(new Expectations() {{
            one(set1).matching(closure);
            will(returnValue(filtered1));
            one(set2).matching(closure);
            will(returnValue(filtered2));
        }});

        SourceSet filtered = set.matching(closure);
        assertThat(filtered, instanceOf(CompositeSourceSet.class));
        CompositeSourceSet filteredCompositeSet = (CompositeSourceSet) filtered;
        assertThat(filteredCompositeSet.getSets(), equalTo(toLinkedSet(filtered1, filtered2)));
    }
}
