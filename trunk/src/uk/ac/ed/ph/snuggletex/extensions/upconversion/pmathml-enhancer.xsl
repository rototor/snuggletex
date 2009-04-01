<!--

$Id$

This stylesheet enhances a raw Presentation MathML <math/> element
generated by SnuggleTeX, attempting to infer semantics within basic
mathematical expressions.

This is the first step in any attempts to up-convert to Content MathML
or Maxima input.

See the local:process-group template to see how groupings/precedence
are established.

TODO: <mstyle/> is essentially being treated as neutering its contents... is this a good idea? It's a hard problem to solve in general.

Copyright (c) 2009 The University of Edinburgh
All Rights Reserved

-->
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:s="http://www.ph.ed.ac.uk/snuggletex"
  xmlns:sho="http://www.ph.ed.ac.uk/snuggletex/higher-order"
  xmlns:local="http://www.ph.ed.ac.uk/snuggletex/pmathml-enhancer"
  xmlns:m="http://www.w3.org/1998/Math/MathML"
  xmlns="http://www.w3.org/1998/Math/MathML"
  exclude-result-prefixes="xs m s sho local"
  xpath-default-namespace="http://www.w3.org/1998/Math/MathML">

  <xsl:import href="common.xsl"/>
  <xsl:strip-space elements="m:*"/>

  <!-- ************************************************************ -->

  <!-- Entry point -->
  <xsl:template name="s:enhance-pmathml">
    <xsl:param name="elements" as="element()*"/>
    <xsl:call-template name="local:process-group">
      <xsl:with-param name="elements" select="$elements"/>
    </xsl:call-template>
  </xsl:template>

  <!-- ************************************************************ -->

  <xsl:variable name="local:invertible-elementary-functions" as="xs:string+"
    select="('sin', 'cos', 'tan',
             'sec', 'csc' ,'cot',
             'sinh', 'cosh', 'tanh',
             'sech', 'csch', 'coth')"/>

  <xsl:variable name="local:elementary-functions" as="xs:string+"
    select="($local:invertible-elementary-functions,
            'arcsin', 'arccos', 'arctan',
            'arcsec', 'arccsc', 'arccot',
            'arcsinh', 'arccosh', 'arctanh',
            'arcsech', 'arccsch', 'arccoth',
            'ln', 'log', 'exp')"/>

  <xsl:variable name="local:relation-characters" as="xs:string+"
    select="('=', '&lt;', '&gt;', '|', '&#x2192;', '&#x21d2;',
            '&#x2208;', '&#x2209;', '&#x2224;', '&#x2248;', '&#x2249;',
            '&#x2264;', '&#x2265;', '&#x2260;', '&#x2261;', '&#x2264;',
            '&#x2265;', '&#x226e;', '&#x226f;', '&#x2270;', '&#x2271;',
            '&#x2261;', '&#x2262;', '&#x2248;', '&#x2249;', '&#x2282;',
            '&#x2284;', '&#x2286;', '&#x2288;'
            )"/>

  <xsl:variable name="local:explicit-multiplication-characters" as="xs:string+"
    select="('*', '&#xd7;', '&#x22c5;')"/>

  <xsl:variable name="local:explicit-division-characters" as="xs:string+"
    select="('/', '&#xf7;')"/>

  <xsl:variable name="local:prefix-operators" as="xs:string+"
    select="('&#xac;')"/>

  <!-- NOTE: We're allowing infix operators to act as prefix operators here, even though
       this won't make sense further in the up-conversion process -->
  <xsl:variable name="local:infix-operators" as="xs:string+"
    select="('&#x2227;', '&#x2228;', '+', '-',
             $local:relation-characters,
             $local:explicit-multiplication-characters,
             $local:explicit-division-characters)"/>

  <!-- NOTE: Currently, the only postfix operator is factorial, which is handled in a special way.
       But I'll keep this more general logic for the time being as it gives nicer symmetry with prefix
       operators. -->
  <xsl:variable name="local:postfix-operators" as="xs:string+"
    select="('!')"/>

  <xsl:function name="local:is-operator" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:sequence select="boolean($element[self::mo])"/>
  </xsl:function>

  <!-- Tests whether the given element is an <mo/> infix operator as listed above -->
  <xsl:function name="local:is-infix-operator" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:sequence select="local:is-operator($element) and $element=$local:infix-operators"/>
  </xsl:function>

  <!-- Additionally tests that the given element is applied in infix context -->
  <xsl:function name="local:is-infix-operator-application" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:variable name="previous" as="element()?" select="$element/preceding-sibling::*[1]"/>
    <xsl:sequence select="local:is-infix-operator($element) and not(exists($previous) and local:is-operator($previous))"/>
  </xsl:function>

  <xsl:function name="local:is-factorial-operator" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:sequence select="boolean($element[self::mo and .='!'])"/>
  </xsl:function>

  <xsl:function name="local:is-elementary-function" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:sequence select="boolean($element[self::mi and $local:elementary-functions=string(.)])"/>
  </xsl:function>

  <!--
  Tests for the equivalent of \sin, \sin^{.}, \log_{.}, \log_{.}^{.}
  Result need not make any actual sense!
  -->
  <xsl:function name="local:is-supported-function" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:sequence select="local:is-elementary-function($element)
      or $element[self::msup and local:is-elementary-function(*[1])]
      or $element[self::msub and *[1][self::mi and .='log']]
      or $element[self::msubsup and *[1][self::mi and .='log']]"/>
  </xsl:function>

  <xsl:function name="local:is-prefix-operator" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:sequence select="boolean($element[self::mo and $local:prefix-operators=string(.)])"/>
  </xsl:function>

  <xsl:function name="local:is-prefix-or-function" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:sequence select="boolean(local:is-supported-function($element) or local:is-prefix-operator($element))"/>
  </xsl:function>

  <xsl:function name="local:is-postfix-operator" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:sequence select="boolean($element[self::mo and $local:postfix-operators=string(.)])"/>
  </xsl:function>

  <!--
  We'll say that an element starts a "no-infix group" if:

  1. It is either the first in a sequence of siblings
  OR 2. It is a prefix operator or function and doesn't immediately follow a prefix operator or function
  OR 3. It is neither a prefix operator/function nor postfix operator and follows a postfix operator

  Such an element will thus consist of:

  prefix-operator-or-function* implicit-multiplication* postfix-opeator*

  -->
  <xsl:function name="local:is-no-infix-group-starter" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:variable name="previous" as="element()?" select="$element/preceding-sibling::*[1]"/>
    <xsl:sequence select="boolean(
      not(exists($previous))
      or (local:is-prefix-or-function($element) and not(local:is-prefix-or-function($previous)))
      or (not(local:is-prefix-or-function($element)) and not(local:is-postfix-operator($element))
        and local:is-postfix-operator($previous)))"/>
  </xsl:function>

  <!-- ************************************************************ -->
  <!-- Grouping by implied precedence -->

  <xsl:template name="local:process-group">
    <xsl:param name="elements" as="element()*" required="yes"/>
    <xsl:choose>
      <!-- Infix Operator Grouping -->
      <xsl:when test="$elements[local:is-matching-infix-mo(., ('&#x2228;'))]">
        <!-- Logical Or -->
        <xsl:call-template name="local:group-associative-infix-mo">
          <xsl:with-param name="elements" select="$elements"/>
          <xsl:with-param name="match" select="('&#x2228;')"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$elements[local:is-matching-infix-mo(., ('&#x2227;'))]">
        <!-- Logical And -->
        <xsl:call-template name="local:group-associative-infix-mo">
          <xsl:with-param name="elements" select="$elements"/>
          <xsl:with-param name="match" select="('&#x2227;')"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$elements[local:is-matching-infix-mo(., $local:relation-characters)]">
        <!-- Relations are all kept at the same precedence level -->
        <xsl:call-template name="local:group-associative-infix-mo">
          <xsl:with-param name="elements" select="$elements"/>
          <xsl:with-param name="match" select="$local:relation-characters"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$elements[local:is-matching-infix-mo(., ('+'))]">
        <!-- Addition -->
        <xsl:call-template name="local:group-associative-infix-mo">
          <xsl:with-param name="elements" select="$elements"/>
          <xsl:with-param name="match" select="('+')"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$elements[local:is-matching-infix-mo(., ('-'))]">
        <!-- Subtraction -->
        <xsl:call-template name="local:group-left-associative-infix-mo">
          <xsl:with-param name="elements" select="$elements"/>
          <xsl:with-param name="match" select="('-')"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$elements[local:is-matching-infix-mo(., $local:explicit-multiplication-characters)]">
        <!-- Explicit Multiplication, detected in various ways -->
        <xsl:call-template name="local:group-associative-infix-mo">
          <xsl:with-param name="elements" select="$elements"/>
          <xsl:with-param name="match" select="$local:explicit-multiplication-characters"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$elements[local:is-matching-infix-mo(., $local:explicit-division-characters)]">
        <!-- Explicit Division -->
        <xsl:call-template name="local:group-left-associative-infix-mo">
          <xsl:with-param name="elements" select="$elements"/>
          <xsl:with-param name="match" select="$local:explicit-division-characters"/>
        </xsl:call-template>
      </xsl:when>
      <!-- Other Groupings -->
      <xsl:when test="$elements[self::mspace]">
        <!-- Any <mspace/> is kept but interpreted as an implicit multiplication as well -->
        <xsl:call-template name="local:handle-mspace-group">
          <xsl:with-param name="elements" select="$elements"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$elements[1][local:is-infix-operator(.)]">
        <!-- An infix operator being used as in prefix context -->
        <xsl:call-template name="local:apply-prefix-operator">
          <xsl:with-param name="elements" select="$elements"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="count($elements) &gt; 1">
        <!-- Need to infer function applications and multiplications, leave other operators as-is -->
        <xsl:call-template name="local:handle-no-infix-group">
          <xsl:with-param name="elements" select="$elements"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="count($elements)=1">
        <!-- "Atom" -->
        <xsl:apply-templates select="$elements[1]" mode="enhance-pmathml"/>
      </xsl:when>
      <xsl:when test="empty($elements)">
        <!-- Empty -> empty -->
      </xsl:when>
      <xsl:otherwise>
        <!-- Based on the logic above, this can't actually happen! -->
        <xsl:message terminate="yes">
          Unexpected logic branch in local:process-group template
        </xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!--
  Tests whether the given element is a particular <mo/> applied strictly
  in infix fashion
  -->
  <xsl:function name="local:is-matching-infix-mo" as="xs:boolean">
    <xsl:param name="element" as="element()"/>
    <xsl:param name="match" as="xs:string+"/>
    <xsl:sequence select="boolean(local:is-infix-operator-application($element) and $element=$match)"/>
  </xsl:function>

  <!-- Groups an associative infix <mo/> operator -->
  <xsl:template name="local:group-associative-infix-mo">
    <xsl:param name="elements" as="element()+" required="yes"/>
    <xsl:param name="match" as="xs:string+" required="yes"/>
    <xsl:for-each-group select="$elements" group-adjacent="local:is-matching-infix-mo(., $match)">
      <xsl:choose>
        <xsl:when test="current-grouping-key()">
          <!-- Copy the matching operator -->
          <xsl:copy-of select="."/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:call-template name="s:maybe-wrap-in-mrow">
            <xsl:with-param name="elements" as="element()*">
              <xsl:call-template name="local:process-group">
                <xsl:with-param name="elements" select="current-group()"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>

  <!-- Groups a left- but not right-associative infix <mo/> operator -->
  <xsl:template name="local:group-left-associative-infix-mo">
    <xsl:param name="elements" as="element()+" required="yes"/>
    <xsl:param name="match" as="xs:string+" required="yes"/>
    <xsl:variable name="operators" select="$elements[local:is-matching-infix-mo(., $match)]" as="element()+"/>
    <xsl:variable name="operator-count" select="count($operators)" as="xs:integer"/>
    <xsl:choose>
      <xsl:when test="$operator-count != 1">
        <!-- Something like 'a o b o c'. We handle this recursively as '(a o b) o c' -->
        <xsl:variable name="last-operator" select="$operators[position()=last()]" as="element()"/>
        <xsl:variable name="before-last-operator" select="$elements[. &lt;&lt; $last-operator]" as="element()+"/>
        <xsl:variable name="after-last-operator" select="$elements[. &gt;&gt; $last-operator]" as="element()*"/>
        <mrow>
          <xsl:call-template name="local:group-left-associative-infix-mo">
            <xsl:with-param name="elements" select="$before-last-operator"/>
            <xsl:with-param name="match" select="$match"/>
          </xsl:call-template>
        </mrow>
        <xsl:copy-of select="$last-operator"/>
        <xsl:call-template name="local:process-group">
          <xsl:with-param name="elements" select="$after-last-operator"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <!-- Only one operator, so it'll be 'a o b' (or more pathologically 'a o').
             We will allow the pathological cases here. -->
        <xsl:variable name="operator" select="$operators[1]" as="element()"/>
        <xsl:variable name="left-operand" select="$elements[. &lt;&lt; $operator]" as="element()*"/>
        <xsl:variable name="right-operand" select="$elements[. &gt;&gt; $operator]" as="element()*"/>
        <xsl:call-template name="local:process-group">
          <xsl:with-param name="elements" select="$left-operand"/>
        </xsl:call-template>
        <xsl:copy-of select="$operator"/>
        <xsl:call-template name="local:process-group">
          <xsl:with-param name="elements" select="$right-operand"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Groups up a prefix operator, provided it is being applied to something -->
  <xsl:template name="local:apply-prefix-operator">
    <xsl:param name="elements" as="element()+" required="yes"/>
    <xsl:choose>
      <xsl:when test="$elements[2]">
        <mrow>
          <xsl:copy-of select="$elements[1]"/>
          <xsl:call-template name="s:maybe-wrap-in-mrow">
            <xsl:with-param name="elements" as="element()*">
              <xsl:call-template name="local:process-group">
                <xsl:with-param name="elements" select="$elements[position()!=1]"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
        </mrow>
      </xsl:when>
      <xsl:otherwise>
        <xsl:copy-of select="$elements[1]"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- <mspace/> as explicit multiplication -->
  <xsl:template name="local:handle-mspace-group">
    <xsl:param name="elements" as="element()+" required="yes"/>
    <xsl:for-each-group select="$elements" group-adjacent="boolean(self::mspace)">
      <xsl:choose>
        <xsl:when test="current-grouping-key()">
          <xsl:copy-of select="."/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:if test="position()!=1">
            <!-- Add in InvisibleTimes -->
            <mo>&#x2062;</mo>
          </xsl:if>
          <!-- Then process as normal -->
          <xsl:call-template name="s:maybe-wrap-in-mrow">
            <xsl:with-param name="elements" as="element()*">
              <xsl:call-template name="local:process-group">
                <xsl:with-param name="elements" select="current-group()"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>

  <xsl:template name="local:handle-no-infix-group">
    <xsl:param name="elements" as="element()+" required="yes"/>
    <xsl:for-each-group select="$elements" group-starting-with="*[local:is-no-infix-group-starter(.)]">
      <!-- Add an invisible times if we're the second multiplicative group -->
      <xsl:if test="position()!=1">
        <mo>&#x2062;</mo>
      </xsl:if>
      <!-- Apply prefix operators and functions from start of group -->
      <xsl:call-template name="s:maybe-wrap-in-mrow">
        <xsl:with-param name="elements" as="element()*">
          <xsl:call-template name="local:apply-prefix-functions-and-operators">
            <xsl:with-param name="elements" select="current-group()"/>
          </xsl:call-template>
        </xsl:with-param>
      </xsl:call-template>
    </xsl:for-each-group>
  </xsl:template>

  <xsl:template name="local:apply-prefix-functions-and-operators">
    <xsl:param name="elements" as="element()+" required="yes"/>
    <xsl:variable name="first-element" as="element()" select="$elements[1]"/>
    <xsl:variable name="after-first-element" as="element()*" select="$elements[position()!=1]"/>
    <xsl:choose>
      <xsl:when test="local:is-supported-function($first-element) and exists($after-first-element)">
        <!-- This is a (prefix) function application. Copy the operator as-is -->
        <xsl:copy-of select="$first-element"/>
        <!-- Add an "Apply Function" operator -->
        <mo>&#x2061;</mo>
        <!-- Process the rest recursively -->
        <xsl:call-template name="local:apply-prefix-functions-and-operators">
          <xsl:with-param name="elements" select="$after-first-element"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="local:is-prefix-operator($first-element)">
        <!-- This is a prefix operator. Apply to everything that follows. -->
        <xsl:copy-of select="$first-element"/>
        <xsl:if test="exists($after-first-element)">
          <xsl:call-template name="s:maybe-wrap-in-mrow">
            <xsl:with-param name="elements" as="element()*">
              <xsl:call-template name="local:apply-prefix-functions-and-operators">
                <xsl:with-param name="elements" select="$after-first-element"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
        </xsl:if>
      </xsl:when>
      <xsl:otherwise>
        <!-- This is everything after any prefixes but before any postfixes -->
        <xsl:call-template name="s:maybe-wrap-in-mrow">
          <xsl:with-param name="elements" as="element()*">
            <xsl:call-template name="local:apply-postfix-operators">
              <xsl:with-param name="elements" select="$elements"/>
            </xsl:call-template>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="local:apply-postfix-operators">
    <xsl:param name="elements" as="element()*" required="yes"/>
    <xsl:variable name="last-element" as="element()?" select="$elements[position()=last()]"/>
    <xsl:variable name="before-last-element" as="element()*" select="$elements[position()!=last()]"/>
    <xsl:choose>
      <xsl:when test="not(exists($last-element))">
        <!-- Nothing left to do -->
      </xsl:when>
      <xsl:when test="$last-element[local:is-factorial-operator(.)]">
        <!-- The factorial operator only binds to the last resulting subexpression -->
        <xsl:call-template name="local:apply-factorial">
          <xsl:with-param name="elements" as="element()*">
            <xsl:call-template name="local:apply-postfix-operators">
              <xsl:with-param name="elements" select="$before-last-element"/>
            </xsl:call-template>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$last-element[local:is-postfix-operator(.)]">
        <!-- General Postfix operator. Bind to everything preceding.
             NOTE: This is not tested yet as we don't have any such operators! -->
        <xsl:call-template name="local:apply-postfix-operators">
          <xsl:with-param name="elements" select="$before-last-element"/>
        </xsl:call-template>
        <xsl:copy-of select="$last-element"/>
      </xsl:when>
      <xsl:otherwise>
        <!-- We're in the "middle" of the expression, which we assume is implicit multiplication -->
        <xsl:call-template name="local:handle-implicit-multiplicative-group">
          <xsl:with-param name="elements" select="$elements"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!--
  Applying a factorial is actually pretty complicated as it only binds with
  the preceding item. We can also have multiple factorials mixed with
  postfix operators at the same level.

  Example 1:

  <mi>x</mi>
  <mo>!</mo>

  becomes:

  <mrow>
    <mi>x</mi>
    <mo>!</mo>
  </mrow>

  Example 2:

  <mn>2</mn>
  <mi>x</mi>
  <mo>!</mo>

  becomes:

  <mn>2</mn>
  <mo>&InvisibleTimes;</mo>
  <mrow>
    <mi>x</mi>
    <mo>!</mo>
  </mrow>

  Example 3:

  <mi>x</mi>
  <mo>!</mo>
  <mo>!</mo>

  becomes:

  <mrow>
    <mrow>
      <mi>x</mi>
      <mo>!</mo>
    </mrow>
    <mo>!</mo>
  </mrow>

  Example 4:

  <mn>2</mn>
  <mi>x</mi>
  <mo>!</mo>
  <mo>!</mo>

  becomes:

  <mn>2</mn>
  <mo>&InvisibleTimes;</mo>
  <mrow>
    <mrow>
      <mi>x</mi>
      <mo>!</mo>
    </mrow>
    <mo>!</mo>
  </mrow>

  Example 5:

  <mn>2</mn>
  <mi>x</mi>
  <mo>!</mo>
  <mo>#</mo> (some postfix operator #)
  <mo>!</mo>

  becomes:

  <mrow>
    <mrow>
      <mrow>
        <mn>2</mn>
        <mo>&InvisibleTimes;</mo>
        <mrow>
          <mi>x</mi>
          <mo>!</mo>
        </mrow>
      </mrow>
      <mo>#</mo>
    </mrow>
    <mo>!</mo>
  </mrow>
  -->
  <xsl:template name="local:apply-factorial">
    <!-- NB: This doesn't include the actual factorial operator itself! -->
    <xsl:param name="elements" as="element()*" required="yes"/>
    <xsl:choose>
      <xsl:when test="not(exists($elements))">
        <!-- Unapplied Factorial -->
        <mo>!</mo>
      </xsl:when>
      <xsl:otherwise>
        <xsl:variable name="last-element" as="element()" select="$elements[position()=last()]"/>
        <xsl:variable name="before-last-element" as="element()*" select="$elements[position()!=last()]"/>
        <xsl:choose>
          <xsl:when test="$last-element[self::mrow and not(local:is-postfix-operator($last-element/*[position()=last()]))]
              and not(exists($before-last-element))">
              <!-- This is where we're processing a single <mrow/> whose last element
              is not a postfix operator. In this case, we just re-process by
              descending into it. -->
            <xsl:call-template name="local:apply-factorial">
              <xsl:with-param name="elements" select="$last-element/*"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <!-- Otherwise, bind the factorial only to the last element -->
            <xsl:copy-of select="$before-last-element"/>
            <mrow>
              <xsl:copy-of select="$last-element"/>
              <mo>!</mo>
            </mrow>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="local:handle-implicit-multiplicative-group">
    <xsl:param name="elements" as="element()*" required="yes"/>
    <xsl:call-template name="s:maybe-wrap-in-mrow">
      <xsl:with-param name="elements" as="element()*">
        <xsl:for-each select="$elements">
          <xsl:if test="position()!=1">
            <!-- Add an "Invisible Times" -->
            <mo>&#x2062;</mo>
          </xsl:if>
          <!-- Descend into the element itself -->
          <xsl:apply-templates select="." mode="enhance-pmathml"/>
        </xsl:for-each>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <!-- ************************************************************ -->
  <!-- Templates for explicit MathML elements -->

  <!--
  Special template to handle the case where the DOM building process
  has created an "apply function" of the following form:

  <mrow>
    <mi>f</mi>
    <mo>&ApplyFunction;</mo>
    <mfenced>
        ... args ...
    </mfenced>
  </mrow>

  In this case, we keep the same general structure intact but
  descend into enhancing the arguments.

  (I have added this primarily for the MathAssess project, but
  it might have utility with custom DOM handlers as well.)
  -->
  <xsl:template match="mrow[count(*)=3 and *[1][self::mi]
      and *[2][self::mo and .='&#x2061;']
      and *[3][self::mfenced]]" mode="enhance-pmathml">
    <xsl:copy>
      <xsl:copy-of select="*[1]"/>
      <xsl:copy-of select="*[2]"/>
      <mfenced open="{*[3]/@open}" close="{*[3]/@close}">
        <xsl:apply-templates select="*[3]/*" mode="enhance-pmathml"/>
      </mfenced>
    </xsl:copy>
  </xsl:template>

  <!-- Container elements with unrestricted content -->
  <xsl:template match="mrow|msqrt" mode="enhance-pmathml">
    <!-- Process contents as normal -->
    <xsl:variable name="processed-contents" as="element()*">
      <xsl:call-template name="local:process-group">
        <xsl:with-param name="elements" select="*"/>
      </xsl:call-template>
    </xsl:variable>
    <!-- If contents consists of a single <mrow/>, strip it off and descend down -->
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:choose>
        <xsl:when test="count($processed-contents)=1 and $processed-contents[1][self::mrow]">
          <xsl:copy-of select="$processed-contents/*"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:copy-of select="$processed-contents"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:copy>
  </xsl:template>

  <!-- Default template for other MathML elements -->
  <xsl:template match="*" mode="enhance-pmathml">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:apply-templates mode="enhance-pmathml"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="text()" mode="enhance-pmathml">
    <xsl:copy-of select="."/>
  </xsl:template>

</xsl:stylesheet>
