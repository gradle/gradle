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
    <xsl:import href="html/docbook.xsl"/>
    <xsl:import href="userGuideHtmlCommon.xsl"/>

    <!--
      Customize HTML page titles to include "User Guide" and version to help
      with Google results. See issue doc-portal#9.
    -->
    <xsl:template match="book" mode="object.title.markup.textonly">
        <xsl:value-of select="bookinfo/title"/>
        <xsl:text> Version </xsl:text>
        <xsl:value-of select="bookinfo/releaseinfo"/>
        <xsl:text> (Single Page)</xsl:text>
    </xsl:template>
</xsl:stylesheet>
