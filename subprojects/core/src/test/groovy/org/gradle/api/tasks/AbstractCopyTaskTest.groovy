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
package org.gradle.api.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.util.HelperUtil
import org.jmock.Expectations
import org.junit.Test
import spock.lang.Specification

import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat

class AbstractCopyTaskTest extends Specification {

    TestCopyTask task

    def setup() {
        task = HelperUtil.createTask(TestCopyTask)
    }

    void usesDefaultSourceWhenNoSourceHasBeenSpecified() {
        given:
        def defaultSource = Mock(FileTree)

        when:
        task.defaultSource = defaultSource

        then:
        task.source.is(defaultSource)
    }

    @Test
    public void doesNotUseDefaultSourceWhenSourceHasBeenSpecifiedOnSpec() {
        final FileTree source = context.mock(FileTree.class, "source");
        context.checking(new Expectations() {
            {
                one(task.action).hasSource();
                will(returnValue(true));
                one(task.action).getAllSource();
                will(returnValue(source));
            }
        });
        assertThat(task.getSource(), sameInstance((FileCollection) source));
    }


    @Test
    public void copySpecMethodsDelegateToMainSpecOfCopyAction() {
        context.checking(new Expectations() {
            {
                one(task.action).include("include");
                one(task.action).from("source");
            }
        });

        assertThat(task.include("include"), sameInstance((AbstractCopyTask) task));
        assertThat(task.from("source"), sameInstance((AbstractCopyTask) task));
    }

    static class TestCopyTask extends AbstractCopyTask {
        CopyAction contentVisitor
        FileCollection defaultSource

        protected CopyAction createCopyAction() {
            contentVisitor
        }

    }
}
