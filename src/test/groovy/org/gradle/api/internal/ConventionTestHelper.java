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

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class ConventionTestHelper  {
    private Map expectedConventionMapping;
    private Object expectedConvention;
    private Object expectedProperty;
    private String expectedPropertyName;
    private ConventionAwareHelper conventionAwareHelperMock;

    private Mockery context = new Mockery();

    public ConventionTestHelper() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        conventionAwareHelperMock = context.mock(ConventionAwareHelper.class);
        expectedConventionMapping = new HashMap();
        expectedConvention = new Object();
        expectedProperty = new Object();
        expectedProperty = "somepropname";
    }

    public void checkAll(IConventionAware conventionAwareObject) {
        checkConventionMapping(conventionAwareObject);
        checkProperty(conventionAwareObject);
        checkSetGetConventionMapping(conventionAwareObject);
    }

    public void checkConventionMapping(final IConventionAware conventionAware) {
        context.checking(new Expectations() {{
            one(conventionAwareHelperMock).conventionMapping(expectedConventionMapping); will(returnValue(conventionAware));
        }});
        assert conventionAware == conventionAware.conventionMapping(expectedConventionMapping);
        context.assertIsSatisfied();
    }

    public void checkProperty(IConventionAware conventionAware) {
        context.checking(new Expectations() {{
            one(conventionAwareHelperMock).getConventionValue(expectedPropertyName); will(returnValue(expectedProperty));
        }});
        assert conventionAware.conv(null, expectedPropertyName) == expectedProperty;
        context.assertIsSatisfied();
    }

    public void checkSetGetConventionMapping(IConventionAware conventionAware) {
        context.checking(new Expectations() {{
            one(conventionAwareHelperMock).setConventionMapping(expectedConventionMapping); will(returnValue(expectedProperty));
            one(conventionAwareHelperMock).getConventionValue("conventionMapping"); will(returnValue(expectedConventionMapping));
        }});
        conventionAware.setConventionMapping(expectedConventionMapping);
        assert conventionAware.getConventionMapping() == expectedConventionMapping;
        context.assertIsSatisfied();
    }

    public ConventionAwareHelper getConventionAwareHelperMock() {
        return conventionAwareHelperMock;
    }
}
