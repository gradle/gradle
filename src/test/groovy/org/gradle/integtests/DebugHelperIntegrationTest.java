/*=============================================================================
                    AUTOMATED LOGIC CORPORATION
            Copyright (c) 1999 - 2009 All Rights Reserved
     This document contains confidential/proprietary information.
===============================================================================

   @(#)DebugHelperIntegrationTest

   Author(s) jmurph
   $Log: $    
=============================================================================*/
package org.gradle.integtests;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.gradle.DebugHelper;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;

public class DebugHelperIntegrationTest extends AbstractIntegrationTest
{
    @Test
    public void basicDebugHelper() {
        TestFile testFile = testFile("build.gradle").writelns("task doNothing");
        inTestDirectory().withTasks("doNothing").run();

        DebugHelper debugHelper = new DebugHelper(getTestDir());
        String className = debugHelper.getClassNameForScript(testFile);
        File scriptFile = debugHelper.getScriptForClassName(className);

        assertThat(scriptFile, equalTo((File)testFile));
    }
}

