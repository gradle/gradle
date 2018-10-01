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
                        <svg width="160px" height="36px" viewBox="0 0 191 43" version="1.1" xmlns="http://www.w3.org/2000/svg">
                            <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                <g>
                                    <path d="M46.678556,7.70171633 C45.120512,4.8834998 42.335823,4.04123121 40.3684979,4.00130115 C37.9547479,3.9523229 35.9723457,5.29706115 36.3472787,6.1798499 C36.427757,6.36937016 36.8802727,7.35867186 37.1580527,7.76702066 C37.5601446,8.35790689 38.2799562,7.90392373 38.5320749,7.76908601 C39.2849364,7.36653985 40.0895198,7.23622623 41.0021397,7.34323092 C41.8745205,7.44551482 43.033468,7.9864393 43.8109923,9.4893242 C45.6407254,13.0263802 39.9923667,20.3060435 32.9221581,15.2649224 C25.8518496,10.2238013 18.9763466,11.8941761 15.8634538,12.9102289 C12.7506609,13.9260851 11.3198246,14.9477439 12.5498645,17.3104037 C14.2214368,20.5211347 13.6673747,19.5359637 15.2882237,22.1937715 C17.8627305,26.4150478 23.4981089,20.2511642 23.4981089,20.2511642 C19.3000571,26.4766148 15.7013989,24.9724514 14.3210861,22.7961647 C13.0771672,20.8346742 12.1106287,18.5733148 12.1106287,18.5733148 C1.47790725,22.3458205 4.35465711,39 4.35465711,39 L9.63497235,39 C10.9812366,32.8678834 15.794558,33.0939899 16.6195105,39 L20.6486178,39 C24.2119295,27.0258991 33.2432725,39 33.2432725,39 L38.4954303,39 C37.0244547,30.8318437 41.4498627,28.2655996 44.2387454,23.4777295 C47.0279277,18.6894661 49.6699325,12.8318441 46.678556,7.70171633 L46.678556,7.70171633 Z M33.1312419,23.4719269 C30.3544409,22.5602233 31.347639,19.7803632 31.347639,19.7803632 C31.347639,19.7803632 33.772672,20.5692278 37.0530115,21.6459626 C36.864197,22.5081962 35.2324645,24.1619498 33.1312419,23.4719269 L33.1312419,23.4719269 Z" id="Fill-9" fill="#02303A"></path>
                                    <path d="M35.9919757,21.336932 C36.0769161,22.1603228 35.477836,22.9008903 34.6537814,22.990975 C33.8297268,23.0810597 33.0929635,22.486564 33.0080231,21.663068 C32.9230827,20.8396772 33.5222651,20.0991097 34.3462175,20.009025 C35.1702721,19.9189403 35.9071376,20.513436 35.9919757,21.336932" id="Fill-10" fill="#00303A"></path>
                                    <path d="M72.236,22.3 L72.236,29.32 C71.3559956,29.9680032 70.418005,30.4419985 69.422,30.742 C68.425995,31.0420015 67.3600057,31.192 66.224,31.192 C64.8079929,31.192 63.5260057,30.9720022 62.378,30.532 C61.2299943,30.0919978 60.2500041,29.4800039 59.438,28.696 C58.6259959,27.9119961 58.0000022,26.9760054 57.56,25.888 C57.1199978,24.7999946 56.9,23.6120064 56.9,22.324 C56.9,21.0199935 57.1119979,19.8240054 57.536,18.736 C57.9600021,17.6479946 58.5619961,16.7120039 59.342,15.928 C60.1220039,15.1439961 61.0679944,14.5360022 62.18,14.104 C63.2920056,13.6719978 64.5399931,13.456 65.924,13.456 C66.6280035,13.456 67.2859969,13.5119994 67.898,13.624 C68.5100031,13.7360006 69.0779974,13.889999 69.602,14.086 C70.1260026,14.282001 70.6039978,14.5199986 71.036,14.8 C71.4680022,15.0800014 71.8639982,15.3879983 72.224,15.724 L71.3,17.188 C71.1559993,17.4200012 70.9680012,17.5619997 70.736,17.614 C70.5039988,17.6660003 70.2520014,17.6080008 69.98,17.44 L69.188,16.984 C68.9239987,16.8319992 68.6300016,16.7000006 68.306,16.588 C67.9819984,16.4759994 67.618002,16.3840004 67.214,16.312 C66.809998,16.2399996 66.3440026,16.204 65.816,16.204 C64.9599957,16.204 64.1860035,16.3479986 63.494,16.636 C62.8019965,16.9240014 62.2120024,17.3359973 61.724,17.872 C61.2359976,18.4080027 60.8600013,19.0519962 60.596,19.804 C60.3319987,20.5560038 60.2,21.3959954 60.2,22.324 C60.2,23.316005 60.3419986,24.2019961 60.626,24.982 C60.9100014,25.7620039 61.3099974,26.4219973 61.826,26.962 C62.3420026,27.5020027 62.9639964,27.9139986 63.692,28.198 C64.4200036,28.4820014 65.2319955,28.624 66.128,28.624 C66.7680032,28.624 67.3399975,28.5560007 67.844,28.42 C68.3480025,28.2839993 68.8399976,28.1000012 69.32,27.868 L69.32,24.724 L67.136,24.724 C66.927999,24.724 66.7660006,24.6660006 66.65,24.55 C66.5339994,24.4339994 66.476,24.2920008 66.476,24.124 L66.476,22.3 L72.236,22.3 Z M77.504,20.824 C77.8880019,20.0879963 78.3439974,19.5100021 78.872,19.09 C79.4000026,18.6699979 80.0239964,18.46 80.744,18.46 C81.3120028,18.46 81.7679983,18.5839988 82.112,18.832 L81.92,21.052 C81.8799998,21.1960007 81.8220004,21.2979997 81.746,21.358 C81.6699996,21.4180003 81.5680006,21.448 81.44,21.448 C81.3199994,21.448 81.1420012,21.4280002 80.906,21.388 C80.6699988,21.3479998 80.4400011,21.328 80.216,21.328 C79.8879984,21.328 79.5960013,21.3759995 79.34,21.472 C79.0839987,21.5680005 78.854001,21.7059991 78.65,21.886 C78.445999,22.0660009 78.2660008,22.2839987 78.11,22.54 C77.9539992,22.7960013 77.8080007,23.0879984 77.672,23.416 L77.672,31 L74.708,31 L74.708,18.688 L76.448,18.688 C76.7520015,18.688 76.9639994,18.7419995 77.084,18.85 C77.2040006,18.9580005 77.2839998,19.1519986 77.324,19.432 L77.504,20.824 Z M90.008,25.744 C89.1519957,25.7840002 88.4320029,25.8579995 87.848,25.966 C87.2639971,26.0740005 86.7960018,26.2119992 86.444,26.38 C86.0919982,26.5480008 85.8400008,26.7439989 85.688,26.968 C85.5359992,27.1920011 85.46,27.4359987 85.46,27.7 C85.46,28.2200026 85.6139985,28.5919989 85.922,28.816 C86.2300015,29.0400011 86.6319975,29.152 87.128,29.152 C87.736003,29.152 88.2619978,29.0420011 88.706,28.822 C89.1500022,28.6019989 89.5839979,28.2680022 90.008,27.82 L90.008,25.744 Z M83.216,20.404 C84.6320071,19.1079935 86.33599,18.46 88.328,18.46 C89.0480036,18.46 89.6919972,18.5779988 90.26,18.814 C90.8280028,19.0500012 91.307998,19.3779979 91.7,19.798 C92.092002,20.2180021 92.389999,20.7199971 92.594,21.304 C92.798001,21.8880029 92.9,22.5279965 92.9,23.224 L92.9,31 L91.556,31 C91.2759986,31 91.0600008,30.9580004 90.908,30.874 C90.7559992,30.7899996 90.6360004,30.6200013 90.548,30.364 L90.284,29.476 C89.9719984,29.7560014 89.6680015,30.0019989 89.372,30.214 C89.0759985,30.4260011 88.7680016,30.6039993 88.448,30.748 C88.1279984,30.8920007 87.7860018,31.0019996 87.422,31.078 C87.0579982,31.1540004 86.6560022,31.192 86.216,31.192 C85.6959974,31.192 85.2160022,31.1220007 84.776,30.982 C84.3359978,30.8419993 83.9560016,30.6320014 83.636,30.352 C83.3159984,30.0719986 83.0680009,29.7240021 82.892,29.308 C82.7159991,28.8919979 82.628,28.4080028 82.628,27.856 C82.628,27.5439984 82.6799995,27.2340015 82.784,26.926 C82.8880005,26.6179985 83.0579988,26.3240014 83.294,26.044 C83.5300012,25.7639986 83.8359981,25.5000012 84.212,25.252 C84.5880019,25.0039988 85.0499973,24.7880009 85.598,24.604 C86.1460027,24.4199991 86.7839964,24.2700006 87.512,24.154 C88.2400036,24.0379994 89.0719953,23.9680001 90.008,23.944 L90.008,23.224 C90.008,22.3999959 89.8320018,21.790002 89.48,21.394 C89.1279982,20.997998 88.6200033,20.8 87.956,20.8 C87.4759976,20.8 87.0780016,20.8559994 86.762,20.968 C86.4459984,21.0800006 86.1680012,21.2059993 85.928,21.346 C85.6879988,21.4860007 85.470001,21.6119994 85.274,21.724 C85.077999,21.8360006 84.8600012,21.892 84.62,21.892 C84.411999,21.892 84.2360007,21.8380005 84.092,21.73 C83.9479993,21.6219995 83.8320004,21.4960007 83.744,21.352 L83.216,20.404 Z M103.412,21.832 C103.075998,21.423998 102.710002,21.1360008 102.314,20.968 C101.917998,20.7999992 101.492002,20.716 101.036,20.716 C100.587998,20.716 100.184002,20.7999992 99.824,20.968 C99.4639982,21.1360008 99.1560013,21.3899983 98.9,21.73 C98.6439987,22.0700017 98.4480007,22.5019974 98.312,23.026 C98.1759993,23.5500026 98.108,24.1679964 98.108,24.88 C98.108,25.6000036 98.1659994,26.2099975 98.282,26.71 C98.3980006,27.2100025 98.5639989,27.6179984 98.78,27.934 C98.9960011,28.2500016 99.2599984,28.4779993 99.572,28.618 C99.8840016,28.7580007 100.231998,28.828 100.616,28.828 C101.232003,28.828 101.755998,28.7000013 102.188,28.444 C102.620002,28.1879987 103.027998,27.8240024 103.412,27.352 L103.412,21.832 Z M106.376,13.168 L106.376,31 L104.564,31 C104.171998,31 103.924001,30.8200018 103.82,30.46 L103.568,29.272 C103.071998,29.8400028 102.502003,30.2999982 101.858,30.652 C101.213997,31.0040018 100.464004,31.18 99.608,31.18 C98.9359966,31.18 98.3200028,31.0400014 97.76,30.76 C97.1999972,30.4799986 96.718002,30.0740027 96.314,29.542 C95.909998,29.0099973 95.5980011,28.3520039 95.378,27.568 C95.1579989,26.7839961 95.048,25.888005 95.048,24.88 C95.048,23.9679954 95.1719988,23.1200039 95.42,22.336 C95.6680012,21.5519961 96.0239977,20.8720029 96.488,20.296 C96.9520023,19.7199971 97.5079968,19.2700016 98.156,18.946 C98.8040032,18.6219984 99.531996,18.46 100.34,18.46 C101.028003,18.46 101.615998,18.5679989 102.104,18.784 C102.592002,19.0000011 103.027998,19.2919982 103.412,19.66 L103.412,13.168 L106.376,13.168 Z M112.304,13.168 L112.304,31 L109.34,31 L109.34,13.168 L112.304,13.168 Z M123.2,23.428 C123.2,23.0439981 123.146001,22.6820017 123.038,22.342 C122.929999,22.0019983 122.768001,21.7040013 122.552,21.448 C122.335999,21.1919987 122.062002,20.9900007 121.73,20.842 C121.397998,20.6939993 121.012002,20.62 120.572,20.62 C119.715996,20.62 119.042002,20.8639976 118.55,21.352 C118.057998,21.8400024 117.744001,22.5319955 117.608,23.428 L123.2,23.428 Z M117.548,25.216 C117.596,25.8480032 117.707999,26.3939977 117.884,26.854 C118.060001,27.3140023 118.291999,27.6939985 118.58,27.994 C118.868001,28.2940015 119.209998,28.5179993 119.606,28.666 C120.002002,28.8140007 120.439998,28.888 120.92,28.888 C121.400002,28.888 121.813998,28.8320006 122.162,28.72 C122.510002,28.6079994 122.813999,28.4840007 123.074,28.348 C123.334001,28.2119993 123.561999,28.0880006 123.758,27.976 C123.954001,27.8639994 124.143999,27.808 124.328,27.808 C124.576001,27.808 124.759999,27.8999991 124.88,28.084 L125.732,29.164 C125.403998,29.5480019 125.036002,29.8699987 124.628,30.13 C124.219998,30.3900013 123.794002,30.5979992 123.35,30.754 C122.905998,30.9100008 122.454002,31.0199997 121.994,31.084 C121.533998,31.1480003 121.088002,31.18 120.656,31.18 C119.799996,31.18 119.004004,31.0380014 118.268,30.754 C117.531996,30.4699986 116.892003,30.0500028 116.348,29.494 C115.803997,28.9379972 115.376002,28.2500041 115.064,27.43 C114.751998,26.6099959 114.596,25.6600054 114.596,24.58 C114.596,23.7399958 114.731999,22.9500037 115.004,22.21 C115.276001,21.4699963 115.665997,20.8260027 116.174,20.278 C116.682003,19.7299973 117.301996,19.2960016 118.034,18.976 C118.766004,18.6559984 119.591995,18.496 120.512,18.496 C121.288004,18.496 122.003997,18.6199988 122.66,18.868 C123.316003,19.1160012 123.879998,19.4779976 124.352,19.954 C124.824002,20.4300024 125.193999,21.0139965 125.462,21.706 C125.730001,22.3980035 125.864,23.1879956 125.864,24.076 C125.864,24.5240022 125.816,24.8259992 125.72,24.982 C125.624,25.1380008 125.440001,25.216 125.168,25.216 L117.548,25.216 Z M149.576,22.504 C149.576,23.8160066 149.380002,24.9959948 148.988,26.044 C148.595998,27.0920052 148.044004,27.9839963 147.332,28.72 C146.619996,29.4560037 145.766005,30.019998 144.77,30.412 C143.773995,30.804002 142.676006,31 141.476,31 L135.272,31 L135.272,14.02 L141.476,14.02 C142.676006,14.02 143.773995,14.215998 144.77,14.608 C145.766005,15.000002 146.619996,15.5639963 147.332,16.3 C148.044004,17.0360037 148.595998,17.9279948 148.988,18.976 C149.380002,20.0240052 149.576,21.1999935 149.576,22.504 Z M148.304,22.504 C148.304,21.3199941 148.140002,20.2640046 147.812,19.336 C147.483998,18.4079954 147.020003,17.6240032 146.42,16.984 C145.819997,16.3439968 145.100004,15.8560017 144.26,15.52 C143.419996,15.1839983 142.492005,15.016 141.476,15.016 L136.508,15.016 L136.508,30.004 L141.476,30.004 C142.492005,30.004 143.419996,29.8360017 144.26,29.5 C145.100004,29.1639983 145.819997,28.6760032 146.42,28.036 C147.020003,27.3959968 147.483998,26.6120046 147.812,25.684 C148.140002,24.7559954 148.304,23.696006 148.304,22.504 Z M157.52,18.868 C158.376004,18.868 159.141997,19.0139985 159.818,19.306 C160.494003,19.5980015 161.063998,20.0119973 161.528,20.548 C161.992002,21.0840027 162.345999,21.7299962 162.59,22.486 C162.834001,23.2420038 162.956,24.0879953 162.956,25.024 C162.956,25.9600047 162.834001,26.8039962 162.59,27.556 C162.345999,28.3080038 161.992002,28.9519973 161.528,29.488 C161.063998,30.0240027 160.494003,30.4359986 159.818,30.724 C159.141997,31.0120014 158.376004,31.156 157.52,31.156 C156.663996,31.156 155.898003,31.0120014 155.222,30.724 C154.545997,30.4359986 153.974002,30.0240027 153.506,29.488 C153.037998,28.9519973 152.682001,28.3080038 152.438,27.556 C152.193999,26.8039962 152.072,25.9600047 152.072,25.024 C152.072,24.0879953 152.193999,23.2420038 152.438,22.486 C152.682001,21.7299962 153.037998,21.0840027 153.506,20.548 C153.974002,20.0119973 154.545997,19.5980015 155.222,19.306 C155.898003,19.0139985 156.663996,18.868 157.52,18.868 Z M157.52,30.256 C158.232004,30.256 158.853997,30.1340012 159.386,29.89 C159.918003,29.6459988 160.361998,29.2960023 160.718,28.84 C161.074002,28.3839977 161.339999,27.8340032 161.516,27.19 C161.692001,26.5459968 161.78,25.824004 161.78,25.024 C161.78,24.231996 161.692001,23.5120032 161.516,22.864 C161.339999,22.2159968 161.074002,21.6620023 160.718,21.202 C160.361998,20.7419977 159.918003,20.3880012 159.386,20.14 C158.853997,19.8919988 158.232004,19.768 157.52,19.768 C156.807996,19.768 156.186003,19.8919988 155.654,20.14 C155.121997,20.3880012 154.678002,20.7419977 154.322,21.202 C153.965998,21.6620023 153.698001,22.2159968 153.518,22.864 C153.337999,23.5120032 153.248,24.231996 153.248,25.024 C153.248,25.824004 153.337999,26.5459968 153.518,27.19 C153.698001,27.8340032 153.965998,28.3839977 154.322,28.84 C154.678002,29.2960023 155.121997,29.6459988 155.654,29.89 C156.186003,30.1340012 156.807996,30.256 157.52,30.256 Z M174.212,20.656 C174.172,20.6960002 174.134,20.7299999 174.098,20.758 C174.062,20.7860001 174.012,20.8 173.948,20.8 C173.868,20.8 173.760001,20.7460005 173.624,20.638 C173.487999,20.5299995 173.304001,20.4120006 173.072,20.284 C172.839999,20.1559994 172.550002,20.0380005 172.202,19.93 C171.853998,19.8219995 171.432002,19.768 170.936,19.768 C170.247997,19.768 169.638003,19.8899988 169.106,20.134 C168.573997,20.3780012 168.124002,20.7279977 167.756,21.184 C167.387998,21.6400023 167.110001,22.1919968 166.922,22.84 C166.733999,23.4880032 166.64,24.215996 166.64,25.024 C166.64,25.8640042 166.737999,26.6079968 166.934,27.256 C167.130001,27.9040032 167.407998,28.4499978 167.768,28.894 C168.128002,29.3380022 168.563997,29.6759988 169.076,29.908 C169.588003,30.1400012 170.155997,30.256 170.78,30.256 C171.356003,30.256 171.837998,30.1880007 172.226,30.052 C172.614002,29.9159993 172.933999,29.7680008 173.186,29.608 C173.438001,29.4479992 173.635999,29.3000007 173.78,29.164 C173.924001,29.0279993 174.044,28.96 174.14,28.96 C174.236,28.96 174.316,28.9999996 174.38,29.08 L174.68,29.464 C174.495999,29.7040012 174.264001,29.927999 173.984,30.136 C173.703999,30.344001 173.388002,30.5239992 173.036,30.676 C172.683998,30.8280008 172.302002,30.9459996 171.89,31.03 C171.477998,31.1140004 171.048002,31.156 170.6,31.156 C169.839996,31.156 169.146003,31.0180014 168.518,30.742 C167.889997,30.4659986 167.350002,30.0660026 166.898,29.542 C166.445998,29.0179974 166.094001,28.3760038 165.842,27.616 C165.589999,26.8559962 165.464,25.9920048 165.464,25.024 C165.464,24.1119954 165.583999,23.2800038 165.824,22.528 C166.064001,21.7759962 166.415998,21.1280027 166.88,20.584 C167.344002,20.0399973 167.909997,19.6180015 168.578,19.318 C169.246003,19.0179985 170.011996,18.868 170.876,18.868 C171.652004,18.868 172.339997,18.9919988 172.94,19.24 C173.540003,19.4880012 174.063998,19.8239979 174.512,20.248 L174.212,20.656 Z M184.064,20.536 C184,20.6480006 183.908001,20.704 183.788,20.704 C183.7,20.704 183.588001,20.6540005 183.452,20.554 C183.315999,20.4539995 183.134001,20.3420006 182.906,20.218 C182.677999,20.0939994 182.396002,19.9820005 182.06,19.882 C181.723998,19.7819995 181.316002,19.732 180.836,19.732 C180.403998,19.732 180.010002,19.7939994 179.654,19.918 C179.297998,20.0420006 178.994001,20.207999 178.742,20.416 C178.489999,20.624001 178.296001,20.8659986 178.16,21.142 C178.023999,21.4180014 177.956,21.7079985 177.956,22.012 C177.956,22.3880019 178.051999,22.6999988 178.244,22.948 C178.436001,23.1960012 178.685998,23.4079991 178.994,23.584 C179.302002,23.7600009 179.655998,23.9119994 180.056,24.04 C180.456002,24.1680006 180.861998,24.2959994 181.274,24.424 C181.686002,24.5520006 182.091998,24.6939992 182.492,24.85 C182.892002,25.0060008 183.245998,25.1999988 183.554,25.432 C183.862002,25.6640012 184.111999,25.9479983 184.304,26.284 C184.496001,26.6200017 184.592,27.0279976 184.592,27.508 C184.592,28.0280026 184.500001,28.5119978 184.316,28.96 C184.131999,29.4080022 183.862002,29.7959984 183.506,30.124 C183.149998,30.4520016 182.712003,30.711999 182.192,30.904 C181.671997,31.096001 181.076003,31.192 180.404,31.192 C179.563996,31.192 178.840003,31.0580013 178.232,30.79 C177.623997,30.5219987 177.080002,30.1720022 176.6,29.74 L176.864,29.332 C176.904,29.2679997 176.95,29.2200002 177.002,29.188 C177.054,29.1559998 177.124,29.14 177.212,29.14 C177.316001,29.14 177.441999,29.2039994 177.59,29.332 C177.738001,29.4600006 177.935999,29.5979993 178.184,29.746 C178.432001,29.8940007 178.737998,30.0319994 179.102,30.16 C179.466002,30.2880006 179.915997,30.352 180.452,30.352 C180.956003,30.352 181.399998,30.2820007 181.784,30.142 C182.168002,30.0019993 182.487999,29.8120012 182.744,29.572 C183.000001,29.3319988 183.193999,29.0500016 183.326,28.726 C183.458001,28.4019984 183.524,28.0600018 183.524,27.7 C183.524,27.299998 183.428001,26.9680013 183.236,26.704 C183.043999,26.4399987 182.792002,26.2160009 182.48,26.032 C182.167998,25.8479991 181.814002,25.6920006 181.418,25.564 C181.021998,25.4359994 180.616002,25.3080006 180.2,25.18 C179.783998,25.0519994 179.378002,24.9120008 178.982,24.76 C178.585998,24.6079992 178.232002,24.4160012 177.92,24.184 C177.607998,23.9519988 177.356001,23.6700017 177.164,23.338 C176.971999,23.0059983 176.876,22.5920025 176.876,22.096 C176.876,21.6719979 176.967999,21.264002 177.152,20.872 C177.336001,20.479998 177.597998,20.1360015 177.938,19.84 C178.278002,19.5439985 178.691998,19.3080009 179.18,19.132 C179.668002,18.9559991 180.215997,18.868 180.824,18.868 C181.552004,18.868 182.197997,18.971999 182.762,19.18 C183.326003,19.388001 183.839998,19.7079978 184.304,20.14 L184.064,20.536 Z" fill="#02303A"></path>
                                </g>
                            </g>
                        </svg></a>
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
                                <svg class="site-header__down-arrow site-header__icon-light" width="19" height="11" viewBox="0 0 19 11" xmlns="http://www.w3.org/2000/svg"><title>Open Community Menu</title><path transform="rotate(-180 9.374 5.494)" d="M17.9991 10.422825L9.3741 0.565575 0.7491 10.422825" stroke="#02303A" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
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
                                <svg class="site-header__down-arrow site-header__icon-light" width="19" height="11" viewBox="0 0 19 11" xmlns="http://www.w3.org/2000/svg"><title>Open Community Menu</title><path transform="rotate(-180 9.374 5.494)" d="M17.9991 10.422825L9.3741 0.565575 0.7491 10.422825" stroke="#02303A" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                            <div class="site-header__navigation-submenu">
                                <div class="site-header__navigation-submenu-item" itemprop="name">
                                    <a class="site-header__navigation-submenu-item-link" href="https://newsletter.gradle.com" itemprop="url">
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
                            <a target="_top" class="site-header__navigation-link" href="https://gradle.com/enterprise" itemprop="url">Enterprise</a>
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
                            <li itemprop="name"><a href="https://newsletter.gradle.com/" itemprop="url">Newsletter</a></li>
                            <li itemprop="name"><a href="https://twitter.com/gradle" itemprop="url">Twitter</a></li>
                        </ul>
                    </div>
                    <div class="site-footer__link-group">
                        <header><strong>Products</strong></header>
                        <ul class="site-footer__links-list">
                            <li itemprop="name"><a href="https://gradle.com/build-scans" itemprop="url">Build Scans</a></li>
                            <li itemprop="name"><a href="https://gradle.com/build-cache" itemprop="url">Build Cache</a></li>
                            <li itemprop="name"><a href="https://gradle.com/enterprise/resources" itemprop="url">Enterprise Docs</a></li>
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
            <div class="site-footer-secondary">
                <div class="site-footer-secondary__contents">
                    <div class="site-footer__copy">© <a href="https://gradle.com">Gradle Inc.</a>
                        <time>2018</time>
                        All rights reserved.
                    </div>
                    <div class="site-footer__logo"><a href="/">
                        <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40" class="logo-icon">
                            <g style="fill:none;opacity:0.7">
                                <g fill="#11272E">
                                    <path
                                        d="M37.7 7.1C36.4 4.8 34 4 32.4 4 30.4 4 28.7 5.1 29 5.9 29.1 6 29.5 6.8 29.7 7.2 30 7.7 30.6 7.3 30.9 7.2 31.5 6.9 32.2 6.7 32.9 6.8 33.6 6.9 34.6 7.4 35.3 8.6 36.8 11.6 32.1 17.8 26.2 13.5 20.3 9.3 14.5 10.7 11.9 11.5 9.3 12.4 8.1 13.3 9.2 15.3 10.6 18 10.1 17.1 11.5 19.4 13.6 22.9 18.3 17.7 18.3 17.7 14.8 23 11.8 21.7 10.6 19.9 9.6 18.2 8.8 16.3 8.8 16.3 -0.1 19.5 2.3 33.6 2.3 33.6L6.7 33.6C7.9 28.4 11.9 28.6 12.6 33.6L15.9 33.6C18.9 23.5 26.4 33.6 26.4 33.6L30.8 33.6C29.6 26.7 33.3 24.5 35.6 20.5 37.9 16.4 40.1 11.5 37.7 7.1L37.7 7.1ZM26.3 20.5C24 19.7 24.9 17.3 24.9 17.3 24.9 17.3 26.9 18 29.6 18.9 29.5 19.6 28.1 21 26.3 20.5L26.3 20.5Z"/>
                                    <path d="M28.4 18.7C28.5 19.3 28 19.9 27.3 20 26.6 20.1 26 19.6 26 18.9 25.9 18.3 26.4 17.7 27.1 17.6 27.7 17.6 28.3 18 28.4 18.7"/>
                                    <path
                                        d="M37.7 7.1C36.4 4.8 34 4 32.4 4 30.4 4 28.7 5.1 29 5.9 29.1 6 29.5 6.8 29.7 7.2 30 7.7 30.6 7.3 30.9 7.2 31.5 6.9 32.2 6.7 32.9 6.8 33.6 6.9 34.6 7.4 35.3 8.6 36.8 11.6 32.1 17.8 26.2 13.5 20.3 9.3 14.5 10.7 11.9 11.5 9.3 12.4 8.1 13.3 9.2 15.3 10.6 18 10.1 17.1 11.5 19.4 13.6 22.9 18.3 17.7 18.3 17.7 14.8 23 11.8 21.7 10.6 19.9 9.6 18.2 8.8 16.3 8.8 16.3 -0.1 19.5 2.3 33.6 2.3 33.6L6.7 33.6C7.9 28.4 11.9 28.6 12.6 33.6L15.9 33.6C18.9 23.5 26.4 33.6 26.4 33.6L30.8 33.6C29.6 26.7 33.3 24.5 35.6 20.5 37.9 16.4 40.1 11.5 37.7 7.1L37.7 7.1ZM26.3 20.5C24 19.7 24.9 17.3 24.9 17.3 24.9 17.3 26.9 18 29.6 18.9 29.5 19.6 28.1 21 26.3 20.5L26.3 20.5Z"/>
                                    <path d="M28.4 18.7C28.5 19.3 28 19.9 27.3 20 26.6 20.1 26 19.6 26 18.9 25.9 18.3 26.4 17.7 27.1 17.6 27.7 17.6 28.3 18 28.4 18.7"/>
                                </g>
                            </g>
                        </svg>
                    </a></div>
                    <div class="site-footer-secondary__links">
                        <a href="https://gradle.com/careers">Careers</a> |
                        <a href="https://gradle.org/privacy">Privacy</a> |
                        <a href="https://gradle.org/terms">Terms of Service</a> |
                        <a href="https://gradle.org/contact/">Contact</a>
                    </div>
                </div>
            </div>
        </footer>
    </xsl:template>

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

    <xsl:template match='xslthl:word' mode="xslthl">
        <span class="hl-word"><xsl:apply-templates mode="xslthl"/></span>
    </xsl:template>

</xsl:stylesheet>
