<!--
  ~ Copyright 2009 the original author or authors.
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
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:template name="body.attributes">
        <!-- Overridden to remove standard body attributes -->
    </xsl:template>

    <!-- BOOK TITLEPAGE -->

    <xsl:template name="book.titlepage">
        <div class="titlepage">
            <div class="title">
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/title"/>
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/subtitle"/>
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/releaseinfo"/>
            </div>
            <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/author"/>
            <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/copyright"/>
            <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/legalnotice"/>
        </div>
    </xsl:template>

    <xsl:template match="releaseinfo" mode="titlepage.mode">
        <h3 class='releaseinfo'>Version <xsl:value-of select="."/></h3>
    </xsl:template>

    <!-- TABLES -->

    <xsl:template match="table">
        <xsl:param name="class">
            <xsl:apply-templates select="." mode="class.value"/>
        </xsl:param>
        <div class="{$class}">
            <div class="{$class}-contents">
                <table>
                    <xsl:copy-of select="@*[not(local-name()='id')]"/>
                    <xsl:attribute name="id">
                        <xsl:call-template name="object.id"/>
                    </xsl:attribute>
                    <xsl:call-template name="htmlTable"/>
                </table>
            </div>
            <xsl:call-template name="formal.object.heading"/>
        </div>
    </xsl:template>

    <xsl:template match="title" mode="htmlTable">
    </xsl:template>
</xsl:stylesheet>