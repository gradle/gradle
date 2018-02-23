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
package org.gradle.api.file;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Transformer;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Specifies the destination of a copy.
 */
public interface CopyProcessingSpec extends ContentFilterable {
    /**
     * Specifies the destination directory for a copy. The destination is evaluated as per {@link
     * org.gradle.api.Project#file(Object)}.
     *
     * @param destPath Path to the destination directory for a Copy
     * @return this
     */
    CopyProcessingSpec into(Object destPath);

    /**
     * Renames a source file. The closure will be called with a single parameter, the name of the file.
     * The closure should return a String object with a new target name. The closure may return null,
     * in which case the original name will be used.
     *
     * @param closure rename closure
     * @return this
     */
    CopyProcessingSpec rename(Closure closure);

    /**
     * Renames a source file. The function will be called with a single parameter, the name of the file.
     * The function should return a new target name. The function may return null,
     * in which case the original name will be used.
     *
     * @param renamer rename function
     * @return this
     */
    //TODO:rbo Change the parameter type to `Transformer<String, @Nullable String>` once we migrate to Java 8
    CopyProcessingSpec rename(Transformer<String, String> renamer);

    /**
     * Renames files based on a regular expression.  Uses java.util.regex type of regular expressions.  Note that the
     * replace string should use the '$1' syntax to refer to capture groups in the source regular expression.  Files
     * that do not match the source regular expression will be copied with the original name.
     *
     * <p> Example:
     * <pre>
     * rename '(.*)_OEM_BLUE_(.*)', '$1$2'
     * </pre>
     * would map the file 'style_OEM_BLUE_.css' to 'style.css'
     *
     * @param sourceRegEx Source regular expression
     * @param replaceWith Replacement string (use $ syntax for capture groups)
     * @return this
     */
    CopyProcessingSpec rename(String sourceRegEx, String replaceWith);

    /**
     * Renames files based on a regular expression. See {@link #rename(String, String)}.
     *
     * @param sourceRegEx Source regular expression
     * @param replaceWith Replacement string (use $ syntax for capture groups)
     * @return this
     */
    CopyProcessingSpec rename(Pattern sourceRegEx, String replaceWith);

    /**
     * Returns the Unix permissions to use for the target files. {@code null} means that existing
     * permissions are preserved. It is dependent on the copy action implementation whether these permissions
     * will actually be applied.
     *
     * @return The file permissions, or {@code null} if existing permissions should be preserved.
     */
    @Nullable
    Integer getFileMode();

    /**
     * Sets the Unix permissions to use for the target files. {@code null} means that existing
     * permissions are preserved. It is dependent on the copy action implementation whether these permissions
     * will actually be applied.
     *
     * @param mode The file permissions.
     * @return this
     */
    CopyProcessingSpec setFileMode(@Nullable Integer mode);

    /**
     * Returns the Unix permissions to use for the target directories. {@code null} means that existing
     * permissions are preserved. It is dependent on the copy action implementation whether these permissions
     * will actually be applied.
     *
     * @return The directory permissions, or {@code null} if existing permissions should be preserved.
     */
    @Nullable
    Integer getDirMode();

    /**
     * Sets the Unix permissions to use for the target directories. {@code null} means that existing
     * permissions are preserved. It is dependent on the copy action implementation whether these permissions
     * will actually be applied.
     *
     * @param mode The directory permissions.
     * @return this
     */
    CopyProcessingSpec setDirMode(@Nullable Integer mode);

    /**
     * Adds an action to be applied to each file as it is about to be copied into its destination. The action can change
     * the destination path of the file, filter the contents of the file, or exclude the file from the result entirely.
     * Actions are executed in the order added, and are inherited from the parent spec.
     *
     * @param action The action to execute.
     * @return this
     */
    CopyProcessingSpec eachFile(Action<? super FileCopyDetails> action);

    /**
     * Adds an action to be applied to each file as it about to be copied into its destination. The given closure is
     * called with a {@link org.gradle.api.file.FileCopyDetails} as its parameter. Actions are executed in the order
     * added, and are inherited from the parent spec.
     *
     * @param closure The action to execute.
     * @return this
     */
    CopyProcessingSpec eachFile(@DelegatesTo(value=FileCopyDetails.class, strategy = Closure.DELEGATE_FIRST) Closure closure);
}
