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
package org.gradle.api.tasks.javadoc;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.util.WrapUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class GroovydocTest extends AbstractConventionTaskTest {
    private Groovydoc groovydoc;

    @Before
    public void setUp() {
        groovydoc = createTask(Groovydoc.class);
    }

    public ConventionTask getTask() {
        return groovydoc;
    }

    @Test(expected = InvalidUserDataException.class)
    public void addLinkWithNullUrl() {
        groovydoc.link(null, "package");
    }

    @Test(expected = InvalidUserDataException.class)
    public void addLinkWithEmptyPackageList() {
        groovydoc.link("http://www.abc.de");
    }

    @Test(expected = InvalidUserDataException.class)
    public void addLinkWithNullPackage() {
        groovydoc.link("http://www.abc.de", "package", null);
    }

    @Test
    public void addLink() {
        String url1 = "http://www.url1.de";
        String url2 = "http://www.url2.de";
        String package1 = "package1";
        String package2 = "package2";
        String package3 = "package3";

        groovydoc.link(url1, package1, package2);
        groovydoc.link(url2, package3);

        assertThat(groovydoc.getLinks(), equalTo(WrapUtil.toSet(
                new Groovydoc.Link(url1, package1, package2),
                new Groovydoc.Link(url2, package3))));
    }

    @Test
    public void setLinks() {
        String url1 = "http://www.url1.de";
        String url2 = "http://www.url2.de";
        String package1 = "package1";
        String package2 = "package2";

        groovydoc.link(url1, package1);
        Set<Groovydoc.Link> newLinkSet = WrapUtil.toSet(new Groovydoc.Link(url2, package2));
        groovydoc.setLinks(newLinkSet);

        assertThat(groovydoc.getLinks(), equalTo(newLinkSet));
    }

    @Test(expected = InvalidUserDataException.class)
    public void groovyClasspathMustNotBeEmpty() {
        groovydoc.setGroovyClasspath(new SimpleFileCollection());
        groovydoc.generate();
    }
}
