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

package org.gradle.api.tasks;

import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.file.CopyVisitor;
import org.gradle.api.internal.file.CopyActionImpl;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;

import java.io.File;

/**
 * Task for copying files.  This task can also rename and filter files as it copies.
 * The task implements {@link org.gradle.api.file.CopySpec CopySpec} for specifying
 * what to copy.
 * <p>
 * Examples:
 * <pre>
 * task(mydoc, type:Copy) {
 *    from 'src/main/doc'
 *    into 'build/target/doc'
 * }
 *
 *
 * task(initconfig, type:Copy) {
 *    from('src/main/config') {
 *       include '**&#47;*.properties'
 *       include '**&#47;*.xml'
 *       filter(ReplaceTokens, tokens:[version:'2.3.1'])
 *    }
 *    from('src/main/config') {
 *       exclude '**&#47;*.properties', '**&#47;*.xml'  
 *    }
 *    from('src/main/languages') {
 *       rename 'EN_US_(*.)', '$1'
 *    }
 *    into 'build/target/config'
 *    exclude '**&#47;*.bak'
 * }
 * </pre>
 * @author Steve Appling
 */
public class Copy extends AbstractCopyTask {
    private boolean hasDestBeenSet;
    private CopyActionImpl copyAction;

    public Copy() {
        FileResolver fileResolver = ((ProjectInternal) getProject()).getFileResolver();
        setCopyAction(new CopyActionImpl(fileResolver, new CopyVisitor()));
    }

    protected void configureRootSpec() {
        super.configureRootSpec();
        if (!hasDestBeenSet) {
            File destDir = getDestinationDir();
            if (destDir != null) {
                into(destDir);
            }
        }
    }

    public CopyActionImpl getCopyAction() {
        return copyAction;
    }

    public void setCopyAction(CopyActionImpl copyAction) {
        this.copyAction = copyAction;
    }

    @OutputDirectory
    public File getDestinationDir() {
        return getCopyAction().getDestDir();
    }

    public void setDestinationDir(File destinationDir) {
        into(destinationDir);
    }

    // -------------------------------------------------
    // ---- Delegate CopySpec methods to copyAction ----
    // -------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public CopySpec into(Object destDir) {
        hasDestBeenSet = true;
        return super.into(destDir);
    }
}
