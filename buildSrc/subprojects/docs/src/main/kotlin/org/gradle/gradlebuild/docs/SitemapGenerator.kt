/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.docs

import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

import com.redfin.sitemapgenerator.ChangeFreq
import com.redfin.sitemapgenerator.W3CDateFormat
import com.redfin.sitemapgenerator.W3CDateFormat.Pattern
import com.redfin.sitemapgenerator.WebSitemapGenerator
import com.redfin.sitemapgenerator.WebSitemapUrl
import com.redfin.sitemapgenerator.WebSitemapUrl.Options

import java.io.File
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject


@CacheableTask
open class SitemapGenerator @Inject constructor() : SourceTask() {

    @Input
    var urlBase: String = "UNSET"

    @Input
    var lastModifiedDate: Date = Date()

    @Input
    var changeFrequency: ChangeFreq = ChangeFreq.MONTHLY

    @OutputDirectory
    var dest: File = project.file("${project.buildDir}/sitemap")

    fun setDest(input: String) {
        this.dest = project.file(input)
    }
    fun dest(input: String) = setDest(input)
    fun dest(input: File) = {
        this.dest = input
    }

    @TaskAction
    fun generate() {
        val dateFormat = W3CDateFormat(Pattern.DAY)
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"))

        val sitemapGenerator = WebSitemapGenerator.builder(urlBase, dest)
            .dateFormat(dateFormat).build()
        generateUrls(getSource())
            .forEach {
                sitemapGenerator.addUrl(it)
            }
        sitemapGenerator.write()
    }

    private
    fun generateUrls(sourceTree: FileTree): List<WebSitemapUrl> {
        val urls = mutableListOf<WebSitemapUrl>()
        sourceTree.visit(object : FileVisitor {
            override fun visitDir(visitDetails: FileVisitDetails) {}

            override fun visitFile(visitDetails: FileVisitDetails) {
                urls.add(
                    WebSitemapUrl.Options("$urlBase${visitDetails.relativePath}")
                    .lastMod(lastModifiedDate)
                    .changeFreq(changeFrequency)
                    .build())
            }
        })
        return urls.toList()
    }
}
