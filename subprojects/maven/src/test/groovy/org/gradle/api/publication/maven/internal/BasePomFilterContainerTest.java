/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.publication.maven.internal;

import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.PublishFilter;
import org.gradle.internal.Factory;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(JMock.class)
public class BasePomFilterContainerTest {
    private static final String TEST_NAME = "testName";
    
    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private BasePomFilterContainer pomFilterContainer;
    @SuppressWarnings("unchecked")
    protected Factory<MavenPom> mavenPomFactoryMock = context.mock(Factory.class);
    protected MavenPom pomMock;
    protected PomFilter pomFilterMock;
    protected PublishFilter publishFilterMock;


    protected BasePomFilterContainer createPomFilterContainer() {
        return new BasePomFilterContainer(mavenPomFactoryMock);
    }


    @Before
    public void setUp() {
        pomFilterMock = context.mock(PomFilter.class);
        pomMock = context.mock(MavenPom.class);
        publishFilterMock = context.mock(PublishFilter.class);
        context.checking(new Expectations() {
            {
                allowing(mavenPomFactoryMock).create();
                will(returnValue(pomMock));
            }
        });
        pomFilterContainer = createPomFilterContainer();
        pomFilterContainer.setDefaultPomFilter(pomFilterMock);
    }

    @Test
    public void init() {
        pomFilterContainer = new BasePomFilterContainer(mavenPomFactoryMock);
        assertNotNull(pomFilterContainer.getPom());
        assertSame(PublishFilter.ALWAYS_ACCEPT, pomFilterContainer.getFilter());
    }

    @Test(expected = InvalidUserDataException.class)
    public void getFilterWithNullName() {
        pomFilterContainer.filter((String) null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void getPomWithNullName() {
        pomFilterContainer.pom((String) null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addFilterWithNullName() {
        pomFilterContainer.addFilter(null, PublishFilter.ALWAYS_ACCEPT);
    }

    @Test(expected = InvalidUserDataException.class)
    public void addFilterWithNullFilter() {
        pomFilterContainer.addFilter("somename", (PublishFilter) null);
    }

    @Test
    public void getFilter() {
        context.checking(new Expectations() {{
            allowing(pomFilterMock).getFilter(); will(returnValue(publishFilterMock));
        }});
        assertSame(publishFilterMock, pomFilterContainer.getFilter());
    }

    @Test
    public void setFilter() {
        context.checking(new Expectations() {{
            one(pomFilterMock).setFilter(publishFilterMock);
        }});
        pomFilterContainer.setFilter(publishFilterMock);
    }

    @Test
    public void getPom() {
        context.checking(new Expectations() {{
            allowing(pomFilterMock).getPomTemplate(); will(returnValue(pomMock));
        }});
        assertSame(pomMock, pomFilterContainer.getPom());
    }

    @Test
    public void setPom() {
        context.checking(new Expectations() {{
            allowing(pomFilterMock).setPomTemplate(pomMock);
        }});
        pomFilterContainer.setPom(pomMock);
    }


    @Test
    public void addFilter() {
        MavenPom pom = pomFilterContainer.addFilter(TEST_NAME, publishFilterMock);
        assertSame(pom, pomMock);
        assertSame(pomMock, pomFilterContainer.pom(TEST_NAME));
        assertSame(publishFilterMock, pomFilterContainer.filter(TEST_NAME));
    }

    @Test
    public void getActivePomFiltersWithDefault() {
        Iterator<PomFilter> pomFilterIterator = pomFilterContainer.getActivePomFilters().iterator();
        assertSame(pomFilterMock, pomFilterIterator.next());
        assertFalse(pomFilterIterator.hasNext());
    }

    @Test
    public void getActivePomFiltersWithAdditionalFilters() {
        PublishFilter filter1 = context.mock(PublishFilter.class, "filter1");
        PublishFilter filter2 = context.mock(PublishFilter.class, "filter2");
        String testName1 = "name1";
        String testName2 = "name2";
        pomFilterContainer.addFilter(testName1, filter1);
        pomFilterContainer.addFilter(testName2, filter2);
        Set<PomFilter> actualActiveFilters = Sets.newLinkedHashSet(pomFilterContainer.getActivePomFilters());
        assertEquals(2, actualActiveFilters.size());
        checkIfInSet(testName1, filter1, actualActiveFilters);
        checkIfInSet(testName2, filter2, actualActiveFilters);
    }

    private void checkIfInSet(String expectedName, PublishFilter expectedPublishFilter, Set<PomFilter> filters) {
        for (PomFilter pomFilter : filters) {
            if (areEqualPomFilter(expectedName, expectedPublishFilter, pomFilter)) {
                return;
            }
        }
        fail("Not in Set");
    }

    private boolean areEqualPomFilter(String expectedName, PublishFilter expectedPublishFilter, PomFilter pomFilter) {
        if (!expectedName.equals(pomFilter.getName())) {
            return false;
        }
        if (!(expectedPublishFilter == pomFilter.getFilter())) {
            return false;
        }
        return true;
    }
}
