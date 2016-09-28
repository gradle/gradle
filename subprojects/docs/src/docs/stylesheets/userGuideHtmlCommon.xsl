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
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xslthl="http://xslthl.sf.net"
                version="1.0">
    <xsl:import href="highlighting/common.xsl"/>
    <xsl:import href="html/highlight.xsl"/>

    <xsl:output method="html"
                encoding="UTF-8"
                indent="no"/>

    <xsl:param name="use.extensions">1</xsl:param>
    <xsl:param name="toc.section.depth">1</xsl:param>
    <xsl:param name="toc.max.depth">2</xsl:param>
    <xsl:param name="section.autolabel">1</xsl:param>
    <xsl:param name="section.autolabel.max.depth">2</xsl:param>
    <xsl:param name="section.label.includes.component.label">1</xsl:param>
    <xsl:param name="css.decoration">0</xsl:param>
    <xsl:param name="highlight.source" select="1"/>

    <!-- Use custom style sheet content -->
    <xsl:param name="html.stylesheet">DUMMY</xsl:param>
    <xsl:template name="output.html.stylesheets">
        <link href="base.css" rel="stylesheet" type="text/css"/>
        <link href="docs.css" rel="stylesheet" type="text/css"/>
        <link href="userguide.css" rel="stylesheet" type="text/css"/>
    </xsl:template>

    <xsl:param name="generate.toc">
        book toc,title,example
    </xsl:param>

    <xsl:param name="formal.title.placement">
        figure before
        example before
        equation before
        table before
        procedure before
    </xsl:param>

    <xsl:template name="customXref">
        <xsl:param name="target"/>
        <xsl:param name="content">
            <xsl:apply-templates select="$target" mode="object.title.markup"/>
        </xsl:param>
        <a>
            <xsl:attribute name="href">
                <xsl:call-template name="href.target">
                    <xsl:with-param name="object" select="$target"/>
                </xsl:call-template>
            </xsl:attribute>
            <xsl:attribute name="title">
                <xsl:apply-templates select="$target" mode="object.title.markup.textonly"/>
            </xsl:attribute>
            <xsl:value-of select="$content"/>
        </a>
    </xsl:template>

    <!-- Overridden to remove standard body attributes -->
    <xsl:template name="body.attributes">
    </xsl:template>

    <!-- Overridden to remove title attribute from structural divs -->
    <xsl:template match="book|chapter|appendix|section|tip|note" mode="html.title.attribute">
    </xsl:template>

    <!-- ADMONITIONS -->

    <!-- Overridden to remove style from admonitions -->
    <xsl:param name="admon.style">
    </xsl:param>

    <xsl:template match="tip[@role='exampleLocation']" mode="class.value"><xsl:value-of select="@role"/></xsl:template>

    <xsl:param name="admon.textlabel">0</xsl:param>

    <!-- BOOK TITLEPAGE -->

    <!-- Customize the contents of the book titlepage -->
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

    <!-- CHAPTER/APPENDIX TITLES -->

    <!-- Use an <h1> instead of <h2> for chapter titles -->
    <xsl:template name="component.title">
        <h1>
            <xsl:call-template name="anchor">
	            <xsl:with-param name="node" select=".."/>
	            <xsl:with-param name="conditional" select="0"/>
            </xsl:call-template>
            <xsl:apply-templates select=".." mode="object.title.markup"/>
        </h1>
    </xsl:template>

    <!-- Clickable section headers -->
    <!--
      The idea here is to replace the <a> generation for section headers so
      that an 'href' attribute is added alongside the 'name'. They both have
      the same value, hence the anchor becomes self-referencing.

      The rest of the magic is done in CSS.
    -->
    <xsl:template name="anchor">
        <xsl:param name="node" select="."/>
        <xsl:param name="conditional" select="1"/>

        <xsl:choose>
            <xsl:when test="$generate.id.attributes != 0">
                <!-- No named anchors output when this param is set -->
            </xsl:when>
            <xsl:when test="$conditional = 0 or $node/@id or $node/@xml:id">
                <a>
                    <xsl:variable name="refId">
                        <xsl:call-template name="object.id">
                            <xsl:with-param name="object" select="$node"/>
                        </xsl:call-template>
                    </xsl:variable>
                    <xsl:attribute name="name">
                        <xsl:value-of select="$refId"/>
                    </xsl:attribute>
                    <xsl:if test="$node[local-name() = 'section']">
                        <xsl:attribute name="class">section-anchor</xsl:attribute>
                        <xsl:attribute name="href">#<xsl:value-of select="$refId"/></xsl:attribute>
                    </xsl:if>
                </a>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

    <!-- TABLES -->

    <!-- Duplicated from docbook stylesheets, to fix problem where html table does not get a title -->
    <xsl:template match="table">
        <xsl:param name="class">
            <xsl:apply-templates select="." mode="class.value"/>
        </xsl:param>
        <div class="{$class}">
            <xsl:if test="title">
                <xsl:call-template name="formal.object.heading"/>
            </xsl:if>
            <div class="{$class}-contents">
                <table>
                    <xsl:copy-of select="@*[not(local-name()='id')]"/>
                    <xsl:attribute name="id">
                        <xsl:call-template name="object.id"/>
                    </xsl:attribute>
                    <xsl:call-template name="htmlTable"/>
                </table>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="title" mode="htmlTable">
    </xsl:template>

    <!-- CODE HIGHLIGHTING -->

    <xsl:template match='xslthl:keyword' mode="xslthl">
        <span class="hl-keyword"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

    <xsl:template match='xslthl:string' mode="xslthl">
        <span class="hl-string"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

    <xsl:template match='xslthl:comment' mode="xslthl">
        <span class="hl-comment"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

    <xsl:template match='xslthl:number' mode="xslthl">
        <span class="hl-number"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

    <xsl:template match='xslthl:annotation' mode="xslthl">
        <span class="hl-annotation"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

    <xsl:template match='xslthl:doccomment' mode="xslthl">
        <span class="hl-doccomment"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

    <xsl:template match='xslthl:tag' mode="xslthl">
        <span class="hl-tag"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

    <xsl:template match='xslthl:attribute' mode="xslthl">
        <span class="hl-attribute"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

    <xsl:template match='xslthl:value' mode="xslthl">
        <span class="hl-value"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

</xsl:stylesheet>
