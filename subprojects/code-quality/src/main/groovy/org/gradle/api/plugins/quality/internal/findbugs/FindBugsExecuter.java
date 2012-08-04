/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.findbugs;

import edu.umd.cs.findbugs.FindBugs;
import edu.umd.cs.findbugs.FindBugs2;
import edu.umd.cs.findbugs.IFindBugsEngine;
import edu.umd.cs.findbugs.TextUICommandLine;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

public class FindBugsExecuter implements Serializable {

    public FindBugsExecuter() {
    }

    FindBugsResult runFindbugs(FindBugsSpec spec) throws IOException, InterruptedException {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            final List<String> args = spec.getArguments();
            String[] strArray = new String[args.size()];
            args.toArray(strArray);

            Thread.currentThread().setContextClassLoader(FindBugs2.class.getClassLoader());
            FindBugs2 findBugs2 = new FindBugs2();
            TextUICommandLine commandLine = new TextUICommandLine();
            FindBugs.processCommandLine(commandLine, strArray, findBugs2);
            findBugs2.execute();

            return createFindbugsResult(findBugs2);
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    FindBugsResult createFindbugsResult(IFindBugsEngine findBugs) {
            int bugCount = findBugs.getBugCount();
            int missingClassCount = findBugs.getMissingClassCount();
            int errorCount = findBugs.getErrorCount();
            return new FindBugsResult(bugCount, missingClassCount, errorCount);
        }
}