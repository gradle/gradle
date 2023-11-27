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
    <xsl:param name="part.autolabel">0</xsl:param>
    <xsl:param name="chapter.autolabel">0</xsl:param>
    <xsl:param name="section.autolabel">0</xsl:param>
    <xsl:param name="preface.autolabel">0</xsl:param>
    <xsl:param name="figure.autolabel">0</xsl:param>
    <xsl:param name="example.autolabel">0</xsl:param>
    <xsl:param name="table.autolabel">0</xsl:param>
    <xsl:param name="xref.with.number.and.title">0</xsl:param>
    <xsl:param name="css.decoration">0</xsl:param>
    <xsl:param name="highlight.source" select="1"/>

    <!-- Use custom style sheet content -->
    <xsl:param name="html.stylesheet">DUMMY</xsl:param>
    <xsl:template name="output.html.stylesheets">
        <link href="https://fonts.googleapis.com/css?family=Lato:400,400i,700" rel="stylesheet"/>
        <link rel="preconnect" href="//assets.gradle.com" crossorigin="crossorigin"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <link href="base.css" rel="stylesheet" type="text/css"/>
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

    <!-- HEADERS AND FOOTERS -->

    <!-- Use custom header -->
    <xsl:template name="header.navigation">
        <header class="site-layout__header site-header" itemscope="itemscope" itemtype="https://schema.org/WPHeader">
            <nav class="site-header__navigation" itemscope="itemscope" itemtype="https://schema.org/SiteNavigationElement">
                <div class="site-header__navigation-header">
                    <a target="_top" class="logo" href="https://docs.gradle.org" title="Gradle Docs">
                        <svg width="139px" height="43px" viewBox="0 0 278 86" version="1.1" xmlns="http://www.w3.org/2000/svg">
                            <defs>
                                <style>.cls-1 {
                                    fill: #02303a;
                                    }</style>
                            </defs>
                            <title>Gradle</title>
                            <path class="cls-1"
                                  d="M155,56.32V70.27a18.32,18.32,0,0,1-5.59,2.83,21.82,21.82,0,0,1-6.36.89,21.08,21.08,0,0,1-7.64-1.31A17.12,17.12,0,0,1,129.59,69a16.14,16.14,0,0,1-3.73-5.58,18.78,18.78,0,0,1-1.31-7.08,19.58,19.58,0,0,1,1.26-7.14A15.68,15.68,0,0,1,135,40a20.39,20.39,0,0,1,7.45-1.29,22,22,0,0,1,3.92.33,20.43,20.43,0,0,1,3.39.92,15.16,15.16,0,0,1,2.85,1.42A17.3,17.3,0,0,1,155,43.25l-1.84,2.91a1.72,1.72,0,0,1-1.12.84,2,2,0,0,1-1.5-.34L149,45.75a10.49,10.49,0,0,0-1.75-.79,14.33,14.33,0,0,0-2.17-.54,15.29,15.29,0,0,0-2.78-.22,11.91,11.91,0,0,0-4.61.86,9.66,9.66,0,0,0-3.52,2.46,10.9,10.9,0,0,0-2.24,3.84,14.88,14.88,0,0,0-.79,5,15.23,15.23,0,0,0,.85,5.28,11.06,11.06,0,0,0,2.38,3.94A10.15,10.15,0,0,0,138.05,68a14.28,14.28,0,0,0,8.25.44,17.1,17.1,0,0,0,2.94-1.09V61.14h-4.35a1.3,1.3,0,0,1-1-.35,1.15,1.15,0,0,1-.35-.85V56.32Zm10.47-2.93a10.53,10.53,0,0,1,2.72-3.45,5.77,5.77,0,0,1,3.72-1.25,4.5,4.5,0,0,1,2.72.74l-.38,4.41a1.18,1.18,0,0,1-.34.61,1,1,0,0,1-.61.18,6.76,6.76,0,0,1-1.06-.12,8.22,8.22,0,0,0-1.38-.12,5,5,0,0,0-1.74.28,4.37,4.37,0,0,0-1.37.83,5.55,5.55,0,0,0-1.07,1.3,12.26,12.26,0,0,0-.87,1.74V73.61H160V49.14h3.45a1.94,1.94,0,0,1,1.27.32,1.9,1.9,0,0,1,.48,1.16Zm11.36-.84A14.49,14.49,0,0,1,187,48.69a9.92,9.92,0,0,1,3.84.7,8.06,8.06,0,0,1,2.86,2,8.38,8.38,0,0,1,1.78,3,11.64,11.64,0,0,1,.61,3.82V73.61h-2.68a2.64,2.64,0,0,1-1.28-.25,1.72,1.72,0,0,1-.72-1l-.52-1.77a20.25,20.25,0,0,1-1.82,1.47,10.86,10.86,0,0,1-1.83,1.06,10.36,10.36,0,0,1-2,.66,12,12,0,0,1-2.4.22,9.64,9.64,0,0,1-2.86-.41,6.28,6.28,0,0,1-2.27-1.26,5.6,5.6,0,0,1-1.48-2.07,7.38,7.38,0,0,1-.52-2.89,5.7,5.7,0,0,1,.31-1.85,5.3,5.3,0,0,1,1-1.75,8.25,8.25,0,0,1,1.83-1.57,11.17,11.17,0,0,1,2.75-1.29,23.28,23.28,0,0,1,3.81-.9,36.77,36.77,0,0,1,5-.41V58.16a5.35,5.35,0,0,0-1.05-3.64,3.83,3.83,0,0,0-3-1.18,7.3,7.3,0,0,0-2.38.33,9.39,9.39,0,0,0-1.65.75l-1.3.75a2.52,2.52,0,0,1-1.3.34,1.7,1.7,0,0,1-1.05-.32,2.61,2.61,0,0,1-.69-.76Zm13.5,10.61a31.66,31.66,0,0,0-4.3.45,11,11,0,0,0-2.79.82,3.57,3.57,0,0,0-1.5,1.17,2.89,2.89,0,0,0,.47,3.67,3.93,3.93,0,0,0,2.39.67,7,7,0,0,0,3.14-.66,9.52,9.52,0,0,0,2.59-2Zm32.53-25V73.61h-3.6a1.39,1.39,0,0,1-1.48-1.07l-.5-2.36a12.4,12.4,0,0,1-3.4,2.74,9.17,9.17,0,0,1-4.47,1,7.95,7.95,0,0,1-6.55-3.26A11.61,11.61,0,0,1,201,66.79a19.71,19.71,0,0,1-.66-5.34,16.77,16.77,0,0,1,.74-5.06,12.21,12.21,0,0,1,2.13-4,9.88,9.88,0,0,1,3.31-2.69,9.64,9.64,0,0,1,4.34-1,8.63,8.63,0,0,1,3.51.64,9,9,0,0,1,2.6,1.74V38.17ZM217,55.39a5.94,5.94,0,0,0-2.18-1.72,6.54,6.54,0,0,0-2.54-.5,5.68,5.68,0,0,0-2.41.5A4.87,4.87,0,0,0,208,55.19a7.19,7.19,0,0,0-1.17,2.57,14.83,14.83,0,0,0-.4,3.69,16.34,16.34,0,0,0,.34,3.63,7.14,7.14,0,0,0,1,2.44,3.79,3.79,0,0,0,1.58,1.36,5,5,0,0,0,2.07.41,6,6,0,0,0,3.13-.76A9.19,9.19,0,0,0,217,66.36Zm17.67-17.22V73.61h-5.89V38.17ZM245.1,62.11a11.37,11.37,0,0,0,.67,3.26,6.54,6.54,0,0,0,1.38,2.27,5.39,5.39,0,0,0,2,1.33,7.26,7.26,0,0,0,2.61.44,8.21,8.21,0,0,0,2.47-.33,11.51,11.51,0,0,0,1.81-.74c.52-.27,1-.52,1.36-.74a2.31,2.31,0,0,1,1.13-.33,1.21,1.21,0,0,1,1.1.55L261.36,70a9.45,9.45,0,0,1-2.19,1.92,12.18,12.18,0,0,1-2.54,1.24,14,14,0,0,1-2.7.66,18.78,18.78,0,0,1-2.65.19,12.93,12.93,0,0,1-4.75-.85,10.65,10.65,0,0,1-3.82-2.5,11.8,11.8,0,0,1-2.55-4.1,15.9,15.9,0,0,1-.93-5.67,13.55,13.55,0,0,1,.81-4.71,11.34,11.34,0,0,1,2.33-3.84,11,11,0,0,1,3.69-2.59,12.31,12.31,0,0,1,4.93-1,11.86,11.86,0,0,1,4.27.74,9.25,9.25,0,0,1,3.36,2.16,9.84,9.84,0,0,1,2.21,3.48,13,13,0,0,1,.8,4.71,3.82,3.82,0,0,1-.29,1.8,1.19,1.19,0,0,1-1.1.46Zm11.23-3.55A7.28,7.28,0,0,0,256,56.4a5.16,5.16,0,0,0-1-1.77,4.44,4.44,0,0,0-1.63-1.21,5.68,5.68,0,0,0-2.3-.44,5.46,5.46,0,0,0-4,1.45,7.13,7.13,0,0,0-1.87,4.13ZM112.26,14a13.72,13.72,0,0,0-19.08-.32,1.27,1.27,0,0,0-.41.93,1.31,1.31,0,0,0,.38.95l1.73,1.73a1.31,1.31,0,0,0,1.71.12,7.78,7.78,0,0,1,4.71-1.57,7.87,7.87,0,0,1,5.57,13.43C96,40.2,81.41,9.66,48.4,25.37a4.48,4.48,0,0,0-2,6.29l5.66,9.79a4.49,4.49,0,0,0,6.07,1.67l.14-.08-.11.08,2.51-1.41a57.72,57.72,0,0,0,7.91-5.89,1.37,1.37,0,0,1,1.8-.06h0a1.29,1.29,0,0,1,0,2A59.79,59.79,0,0,1,62.11,44l-.09.05-2.51,1.4a7,7,0,0,1-3.47.91,7.19,7.19,0,0,1-6.23-3.57l-5.36-9.24C34.17,40.81,27.93,54.8,31.28,72.5a1.31,1.31,0,0,0,1.29,1.06h6.09A1.3,1.3,0,0,0,40,72.42a8.94,8.94,0,0,1,17.73,0A1.3,1.3,0,0,0,59,73.56h5.94a1.31,1.31,0,0,0,1.3-1.14,8.93,8.93,0,0,1,17.72,0,1.3,1.3,0,0,0,1.29,1.14h5.87a1.3,1.3,0,0,0,1.3-1.28c.14-8.28,2.37-17.79,8.74-22.55C123.15,33.25,117.36,19.12,112.26,14ZM89.79,38.92l-4.2-2.11h0a2.64,2.64,0,1,1,4.2,2.12Z"/>
                        </svg>
                    </a>
                    <div class="site-header__doc-type sr-only">DSL Reference</div>
                    <div class="site-header-version">
                        <xsl:value-of select="//releaseinfo[1]"/>
                    </div>
                    <button type="button" aria-label="Navigation Menu" class="site-header__navigation-button hamburger">
                        <span class="hamburger__bar"></span>
                        <span class="hamburger__bar"></span>
                        <span class="hamburger__bar"></span>
                    </button>
                </div>
                <div class="site-header__navigation-collapsible site-header__navigation-collapsible--collapse">
                    <ul class="site-header__navigation-items">
                        <li class="site-header__navigation-item site-header__navigation-submenu-section" tabindex="0">
                            <span class="site-header__navigation-link">
                                Community
                            </span>
                            <div class="site-header__navigation-submenu">
                                <div class="site-header__navigation-submenu-item" itemprop="name">
                                    <a target="_top" class="site-header__navigation-submenu-item-link" href="https://gradle.org/" itemprop="url">
                                        <span class="site-header__navigation-submenu-item-link-text">Community Home</span>
                                    </a>
                                </div>
                                <div class="site-header__navigation-submenu-item" itemprop="name">
                                    <a target="_top" class="site-header__navigation-submenu-item-link" href="https://discuss.gradle.org/" itemprop="url">
                                        <span class="site-header__navigation-submenu-item-link-text">Community Forums</span>
                                    </a>
                                </div>
                                <div class="site-header__navigation-submenu-item" itemprop="name">
                                    <a target="_top" class="site-header__navigation-submenu-item-link" href="https://plugins.gradle.org" itemprop="url">
                                        <span class="site-header__navigation-submenu-item-link-text">Community Plugins</span>
                                    </a>
                                </div>
                            </div>
                        </li>
                        <li class="site-header__navigation-item" itemprop="name">
                            <a target="_top" class="site-header__navigation-link" href="https://gradle.org/training/" itemprop="url">Training</a>
                        </li>
                        <li class="site-header__navigation-item site-header__navigation-submenu-section" tabindex="0">
                            <span class="site-header__navigation-link">
                                News
                            </span>
                            <div class="site-header__navigation-submenu">
                                <div class="site-header__navigation-submenu-item" itemprop="name">
                                    <a class="site-header__navigation-submenu-item-link" href="https://newsletter.gradle.org" itemprop="url">
                                        <span class="site-header__navigation-submenu-item-link-text">Newsletter</span>
                                    </a>
                                </div>
                                <div class="site-header__navigation-submenu-item" itemprop="name">
                                    <a class="site-header__navigation-submenu-item-link" href="https://blog.gradle.org" itemprop="url">
                                        <span class="site-header__navigation-submenu-item-link-text">Blog</span>
                                    </a>
                                </div>
                                <div class="site-header__navigation-submenu-item">
                                    <a class="site-header__navigation-submenu-item-link" href="https://twitter.com/gradle">
                                        <span class="site-header__navigation-submenu-item-link-text">Twitter</span>
                                    </a>
                                </div>
                            </div>
                        </li>
                        <li class="site-header__navigation-item" itemprop="name">
                            <a target="_top" class="site-header__navigation-link" href="https://gradle.com/enterprise" itemprop="url">Develocity</a>
                        </li>
                        <li class="site-header__navigation-item">
                            <a class="site-header__navigation-link" title="Gradle on GitHub" href="https://github.com/gradle/gradle"><svg width="20" height="20" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg"><title>github</title><path d="M10 0C4.477 0 0 4.477 0 10c0 4.418 2.865 8.166 6.839 9.489.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.342-3.369-1.342-.454-1.155-1.11-1.462-1.11-1.462-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.268 2.75 1.026A9.578 9.578 0 0 1 10 4.836c.85.004 1.705.114 2.504.337 1.909-1.294 2.747-1.026 2.747-1.026.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.579.688.481C17.137 18.163 20 14.418 20 10c0-5.523-4.478-10-10-10" fill="#02303A" fill-rule="evenodd"/></svg></a>
                        </li>
                    </ul>
                </div>
            </nav>
        </header>
    </xsl:template>

    <!-- Use custom footer -->
    <xsl:template name="footer.navigation">
        <footer class="site-layout__footer site-footer" itemscope="itemscope" itemtype="https://schema.org/WPFooter">
            <nav class="site-footer__navigation" itemtype="https://schema.org/SiteNavigationElement">
                <section class="site-footer__links">
                    <div class="site-footer__link-group">
                        <header><strong>Docs</strong></header>
                        <ul class="site-footer__links-list">
                            <li itemprop="name"><a href="/userguide/userguide.html" itemprop="url">User Manual</a></li>
                            <li itemprop="name"><a href="/dsl/" itemprop="url">DSL Reference</a></li>
                            <li itemprop="name"><a href="/release-notes.html" itemprop="url">Release Notes</a></li>
                            <li itemprop="name"><a href="/javadoc/" itemprop="url">Javadoc</a></li>
                        </ul>
                    </div>
                    <div class="site-footer__link-group">
                        <header><strong>News</strong></header>
                        <ul class="site-footer__links-list">
                            <li itemprop="name"><a href="https://blog.gradle.org/" itemprop="url">Blog</a></li>
                            <li itemprop="name"><a href="https://newsletter.gradle.org/" itemprop="url">Newsletter</a></li>
                            <li itemprop="name"><a href="https://twitter.com/gradle" itemprop="url">Twitter</a></li>
                        </ul>
                    </div>
                    <div class="site-footer__link-group">
                        <header><strong>Products</strong></header>
                        <ul class="site-footer__links-list">
                            <li itemprop="name"><a href="https://gradle.com/build-scans" itemprop="url">Build Scan™</a></li>
                            <li itemprop="name"><a href="https://gradle.com/build-cache" itemprop="url">Build Cache</a></li>
                            <li itemprop="name"><a href="https://gradle.com/enterprise/resources" itemprop="url">Develocity Docs</a></li>
                        </ul>
                    </div>
                    <div class="site-footer__link-group">
                        <header><strong>Get Help</strong></header>
                        <ul class="site-footer__links-list">
                            <li itemprop="name"><a href="https://discuss.gradle.org/c/help-discuss" itemprop="url">Forums</a></li>
                            <li itemprop="name"><a href="https://github.com/gradle/" itemprop="url">GitHub</a></li>
                            <li itemprop="name"><a href="https://gradle.org/training/" itemprop="url">Training</a></li>
                            <li itemprop="name"><a href="https://gradle.org/services/" itemprop="url">Services</a></li>
                        </ul>
                    </div>
                </section>
                <section class="site-footer__subscribe-newsletter" id="newsletter-form-container">
                    <header class="newsletter-form__header"><h5>Stay <code>UP-TO-DATE</code> on new features and news</h5></header>
                    <p class="disclaimer">By entering your email, you agree to our <a href="https://gradle.org/terms/">Terms</a> and <a href="https://gradle.org/privacy/">Privacy Policy</a>, including receipt of emails. You can unsubscribe at any time.</p>
                    <div class="newsletter-form__container">
                        <form id="newsletter-form" class="newsletter-form" action="https://go.gradle.com/l/68052/2018-09-07/bk6wml" method="post">
                            <input id="email" class="email" name="email" type="email" placeholder="name@email.com" pattern="[^@\s]+@[^@\s]+\.[^@\s]+" maxlength="255" required=""/>
                            <button id="submit" class="submit" type="submit">Subscribe</button>
                        </form>
                    </div>
                </section>
            </nav>
        </footer>
    </xsl:template>

    <!-- BOOK TITLEPAGE -->

    <!-- Customize the contents of the book titlepage -->
    <xsl:template name="book.titlepage">
        <div class="titlepage" id="header">
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

    <xsl:template match='xslthl:word' mode="xslthl">
        <span class="hl-word"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

</xsl:stylesheet>
