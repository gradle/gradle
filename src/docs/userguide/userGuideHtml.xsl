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
<xsl:stylesheet
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:import href="html/chunkfast.xsl"/>
    <xsl:import href="userGuideCommon.xsl"/>
    <xsl:param name="root.filename">userguide</xsl:param>
    <xsl:param name="chunk.section.depth">0</xsl:param>
    <xsl:param name="chunk.quietly">1</xsl:param>

    <xsl:template name="body.attributes">
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

    <!-- HEADERS AND FOOTERS -->

    <xsl:template name="header.navigation">
        <xsl:param name="next"/>
        <xsl:param name="prev"/>
        <xsl:if test=". != /book">
            <div class='navheader'>
                <xsl:call-template name="navlinks">
                    <xsl:with-param name="next" select="$next"/>
                    <xsl:with-param name="prev" select="$prev"/>
                </xsl:call-template>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="footer.navigation">
        <xsl:param name="next"/>
        <xsl:param name="prev"/>
        <xsl:if test=". != /book">
            <div class='navfooter'>
                <xsl:call-template name="navlinks">
                    <xsl:with-param name="next" select="$next"/>
                    <xsl:with-param name="prev" select="$prev"/>
                </xsl:call-template>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="navlinks">
        <xsl:param name="next"/>
        <xsl:param name="prev"/>
        <div>
            <div class="navbar">
                <xsl:if test="count($prev)>0">
                    <a>
                        <xsl:attribute name="href">
                            <xsl:call-template name="href.target">
                                <xsl:with-param name="object" select="$prev"/>
                            </xsl:call-template>
                        </xsl:attribute>
                        <xsl:attribute name="title">
                            <xsl:apply-templates select="$prev" mode="object.title.markup.textonly"/>
                        </xsl:attribute>
                        <xsl:text>Previous</xsl:text>
                    </a>
                    <span>|</span>
                </xsl:if>
                <xsl:if test="count($next)>0">
                    <a>
                        <xsl:attribute name="href">
                            <xsl:call-template name="href.target">
                                <xsl:with-param name="object" select="$next"/>
                            </xsl:call-template>
                        </xsl:attribute>
                        <xsl:attribute name="title">
                            <xsl:apply-templates select="$next" mode="object.title.markup.textonly"/>
                        </xsl:attribute>
                        <xsl:text>Next</xsl:text>
                    </a>
                    <span>|</span>
                </xsl:if>
                <a>
                    <xsl:attribute name="href">
                        <xsl:call-template name="href.target">
                            <xsl:with-param name="object" select="/book"/>
                        </xsl:call-template>
                    </xsl:attribute>
                    <xsl:text>Contents</xsl:text>
                </a>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>