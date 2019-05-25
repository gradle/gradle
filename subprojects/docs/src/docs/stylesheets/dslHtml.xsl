<!--
  ~ Copyright 2010 the original author or authors.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:import href="html/chunkfast.xsl"/>
    <xsl:import href="userGuideHtmlCommon.xsl"/>

    <xsl:output method="html" doctype-system="http://www.w3.org/TR/html4/strict.dtd"
         doctype-public="-//W3C//DTD HTML 4.01//EN"/>

    <xsl:param name="root.filename">index</xsl:param>
    <xsl:param name="chunk.section.depth">0</xsl:param>
    <xsl:param name="chunk.quietly">1</xsl:param>
    <xsl:param name="use.id.as.filename">1</xsl:param>
    <xsl:param name="section.autolabel">0</xsl:param>
    <xsl:param name="chapter.autolabel">0</xsl:param>
    <xsl:param name="appendix.autolabel">0</xsl:param>
    <xsl:param name="table.autolabel">0</xsl:param>

    <xsl:param name="generate.toc">
        chapter toc,title
    </xsl:param>

    <xsl:template name="formal.object.heading"></xsl:template>

    <!-- customize the page titles -->
    <xsl:template match="book" mode="object.title.markup.textonly">
        <xsl:value-of select="bookinfo/titleabbrev"/>
        <xsl:text> Version </xsl:text>
        <xsl:value-of select="bookinfo/releaseinfo"/>
    </xsl:template>

    <xsl:template match="chapter" mode="object.title.markup.textonly">
        <xsl:value-of select="title"/>
        <xsl:text> - </xsl:text>
        <xsl:apply-templates select="/book" mode="object.title.markup.textonly"/>
    </xsl:template>

    <!-- customize the layout of the html page -->
    <xsl:template name="chunk-element-content">
        <xsl:param name="content">
            <xsl:apply-imports/>
        </xsl:param>

        <html>
            <xsl:call-template name="html.head"></xsl:call-template>

            <body>
                <xsl:call-template name="body.attributes"/>
                <xsl:call-template name="header.navigation"></xsl:call-template>
                <main class="main-content">
                    <nav class="docs-navigation">
                        <div class="search-container">
                            <input type="search" name="q" id="search-input" class="search-input" placeholder="Search Docs"/>
                        </div>
                        <ul>
                            <xsl:apply-templates select="." mode="sidebar"/>
                            <!-- only apply navbar to sections that are not marked with 'noNavBar' -->
                            <xsl:apply-templates select="/book/section[not(@condition) or @condition != 'noNavBar']/table[@role = 'dslTypes']" mode="sidebar"/>
                        </ul>
                    </nav>
                    <xsl:copy-of select="$content"/>
                    <aside class="secondary-navigation"></aside>
                </main>
                <xsl:call-template name="footer.navigation"></xsl:call-template>
            </body>
        </html>
        <xsl:value-of select="$chunk.append"/>
    </xsl:template>

    <!--
      - Navigation sidebar
      -->

    <xsl:template match="book" mode="sidebar">
        <li><a href="../userguide/userguide.html">User Manual Home</a></li>
        <li><a href="index.html" class="active">DSL Reference Home</a></li>
        <li><a href="../release-notes.html">Release Notes</a></li>
        <ul class="sections">
            <xsl:apply-templates select="section" mode="sidebar.link"/>
        </ul>
    </xsl:template>

    <xsl:template match="chapter" mode="sidebar">
        <li><a href="../userguide/userguide.html">User Manual Home</a></li>
        <li><a href="index.html">DSL Reference Home</a></li>
        <li><a href="../release-notes.html">Release Notes</a></li>
        <ul class="sections">
            <xsl:apply-templates select="section[table]" mode="sidebar.link"/>
        </ul>
    </xsl:template>

    <xsl:template match="section" mode="sidebar.link">
        <li>
            <xsl:call-template name="customXref">
                <xsl:with-param name="target" select="."/>
                <xsl:with-param name="content">
                    <xsl:choose>
                        <xsl:when test="titleabbrev"><xsl:value-of select="titleabbrev"/></xsl:when>
                        <xsl:otherwise><xsl:value-of select="title"/></xsl:otherwise>
                    </xsl:choose>
                </xsl:with-param>
            </xsl:call-template>
        </li>
        <xsl:if test="section[table]">
            <ul class='sections'>
                <xsl:apply-templates select="section[table]" mode="sidebar.link"/>
            </ul>
        </xsl:if>
    </xsl:template>

    <xsl:template match="table" mode="sidebar">
        <li>
            <h3><xsl:value-of select="title"/></h3>
        </li>
        <xsl:apply-templates select="tr/td[1]" mode="sidebar"/>
    </xsl:template>

    <xsl:template match="td" mode="sidebar">
        <li>
            <xsl:apply-templates select="link"/>
        </li>
    </xsl:template>

    <!--
      - Customized header for property and method detail sections
      -->

    <xsl:template match="section[@role='detail']/title" mode="titlepage.mode">
        <xsl:variable name="level">
            <xsl:call-template name="section.level">
                <xsl:with-param name="node" select="ancestor::section"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:element name="h{$level+1}">
            <xsl:attribute name="class">signature</xsl:attribute>
            <xsl:call-template name="anchor">
                <xsl:with-param name="node" select="ancestor::section"/>
                <xsl:with-param name="conditional" select="0"/>
            </xsl:call-template>
            <xsl:apply-templates/>
        </xsl:element>
    </xsl:template>

    <!--
      - Customized <segmentedlist> formats
      -->
    <xsl:template match="segmentedlist">
        <div>
            <xsl:call-template name="common.html.attributes"/>
            <xsl:call-template name="anchor"/>
            <table>
                <xsl:apply-templates select="seglistitem/seg" mode="seglist.table"/>
            </table>
        </div>
    </xsl:template>

    <xsl:template match="section[@role='detail']/segmentedlist">
        <div>
            <xsl:call-template name="common.html.attributes"/>
            <xsl:call-template name="anchor"/>
            <dl>
                <xsl:apply-templates select="seglistitem/seg" mode="seglist.list"/>
            </dl>
        </div>
    </xsl:template>

    <xsl:template match="seg" mode="seglist.table">
        <xsl:variable name="segnum" select="count(preceding-sibling::seg)+1"/>
        <xsl:variable name="seglist" select="ancestor::segmentedlist"/>
        <xsl:variable name="segtitles" select="$seglist/segtitle"/>
        <tr>
            <th><xsl:apply-templates select="$segtitles[$segnum=position()]" mode="segtitle-in-seg"/>:</th>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>

    <xsl:template match="seg" mode="seglist.list">
        <xsl:variable name="segnum" select="count(preceding-sibling::seg)+1"/>
        <xsl:variable name="seglist" select="ancestor::segmentedlist"/>
        <xsl:variable name="segtitles" select="$seglist/segtitle"/>
        <dt><xsl:apply-templates select="$segtitles[$segnum=position()]" mode="segtitle-in-seg"/>:</dt>
        <dd><xsl:apply-templates/></dd>
    </xsl:template>
</xsl:stylesheet>
