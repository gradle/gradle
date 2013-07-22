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
package org.gradle.integtests.openapi

import org.gradle.integtests.fixtures.TestResources
import org.gradle.openapi.external.ui.OutputUILordVersion1
import org.gradle.openapi.external.ui.SinglePaneUIVersion1
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.PreconditionVerifier
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

import javax.swing.*
import java.awt.*
import java.util.concurrent.TimeUnit

import static org.hamcrest.Matchers.startsWith

/**
 * Tests aspects of the OutputUILord in OpenAPI
 */
@Requires(TestPrecondition.SWING)
class OutputUILordTest {

    @Rule public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    @Rule public OpenApiFixture openApi = new OpenApiFixture(temporaryFolder)
    @Rule public TestResources resources = new TestResources(temporaryFolder, 'testProject')
    @ClassRule public static PreconditionVerifier verifier = new PreconditionVerifier()

    /**
     * This verifies that you can add file extension to the output lord. This is for
     * highlighting file links in the output. Here, we're just interested in whether
     * or not the functions work via/exists in the Open API. The actual functionality
     * is tested elsewhere.
     */
    @Test
    void testAddingFileExtension() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        OutputUILordVersion1 outputUILord = singlePane.getOutputLord()

        outputUILord.addFileExtension('.txt', ':')
        java.util.List extensions = outputUILord.getFileExtensions()
        Assert.assertTrue(extensions.contains('.txt'))
    }

    /**
     * This verifies that you can add prefixed file extensions to the output lord. This
     * is for highlighting file links in the output. Here, we're just interested in whether
     * or not the functions work via/exists in the Open API. The actual functionality is tested elsewhere.
     */
    @Test
    void testAddingPrefixedFileLink() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        OutputUILordVersion1 outputUILord = singlePane.getOutputLord()

        outputUILord.addPrefixedFileLink("Error Text", "The error is:", ".txt", ":")
    }

    /**
     * This tests setting the font. There's not much here to do other than set it and then
     * get it, making sure its the same. This isn't worried so much about the font itself as
     * much as the open API doesn't have a problem with setting the font.
     */
    @Test
    void testFont() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;  // Can't run this test in headless mode!
        }

        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        OutputUILordVersion1 outputUILord = singlePane.getOutputLord()
        Font font = UIManager.getFont("Button.font")  //this specific font is not important

        //make sure that the above font doesn't happen to be the default font for the output lord. If it
        //is, this test will silently succeed even if it should fail.
        Assert.assertNotSame("Fonts are the same. This test is not setup correctly.", font, outputUILord.getOutputTextFont())

        //now set the new font and then make sure it worked
        outputUILord.setOutputTextFont(font)
    }

    @Test
    void testReExecute() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        OutputUILordVersion1 outputUILord = singlePane.getOutputLord()

        //this starts the execution queue. This also initiates a refresh that we'll ignore later.
        singlePane.aboutToShow()

        BlockingRequestObserver testRequestObserver = new BlockingRequestObserver()
        singlePane.getGradleInterfaceVersion1().addRequestObserver(testRequestObserver)

        //now execute a command
        singlePane.executeCommand("build", "test build")

        //wait for it to complete
        testRequestObserver.waitForRequestExecutionComplete(80, TimeUnit.SECONDS)
        testRequestObserver.reset()

        //now the single command we're trying to test
        outputUILord.reExecuteLastCommand();

        //wait again for it exit
        testRequestObserver.waitForRequestExecutionComplete(80, TimeUnit.SECONDS)

        //make sure it executed the correct request
        Assert.assertThat(testRequestObserver.request.getFullCommandLine(), startsWith('build'))
    }
}
