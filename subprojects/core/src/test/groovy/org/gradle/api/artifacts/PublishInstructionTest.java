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
package org.gradle.api.artifacts;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.gradle.api.InvalidUserDataException;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class PublishInstructionTest {
    @Test
    public void initWithNoArgs() {
        PublishInstruction publishInstruction = new PublishInstruction();
        assertThat(publishInstruction.isUploadDescriptor(), equalTo(false));
        assertThat(publishInstruction.getDescriptorDestination(), equalTo(null));
    }

    @Test
    public void initWithUploadTrueAndFileNotNull() {
        File someFile = new File("somePath");
        PublishInstruction publishInstruction = new PublishInstruction(true, someFile);
        assertThat(publishInstruction.isUploadDescriptor(), equalTo(true));
        assertThat(publishInstruction.getDescriptorDestination(), equalTo(someFile));
    }

    @Test(expected = InvalidUserDataException.class)
    public void initWithUploadFalseAndFileNotNullShouldThrowInvalidUserDataEx() {
        new PublishInstruction(false, new File("somePath"));
    }

    @Test(expected = InvalidUserDataException.class)
    public void initWithUploadTrueAndFileNullShouldThrowInvalidUserDataEx() {
        new PublishInstruction(true, null);
    }

    @Test
    public void initWithUploadFalseAndFileNull() {
        PublishInstruction publishInstruction = new PublishInstruction(false, null);
        assertThat(publishInstruction.isUploadDescriptor(), equalTo(false));
        assertThat(publishInstruction.getDescriptorDestination(), nullValue());
    }

    @Test
    public void equalityAndHash() {
        assertThat(new PublishInstruction(false, null), equalTo(new PublishInstruction(false, null)));
        assertThat(new PublishInstruction(true, new File("somePath")), equalTo(new PublishInstruction(true, new File("somePath"))));
        assertThat(new PublishInstruction(true, new File("somePath")), not(equalTo(new PublishInstruction(true, new File("someOtherPath")))));

        assertThat(new PublishInstruction(true, new File("somePath")).hashCode(),
                equalTo(new PublishInstruction(true, new File("somePath")).hashCode()));
    }
}
