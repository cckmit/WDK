<?xml version="1.0" encoding="UTF-8"?>

<jsp:root version="2.0"
  xmlns:jsp="http://java.sun.com/JSP/Page"
  xmlns:c="http://java.sun.com/jsp/jstl/core"
  xmlns:imp="urn:jsptagdir:/WEB-INF/tags/imp">

  <jsp:directive.attribute name="refer" required="false" 
              description="Page calling this tag. The list of WDK recognized refer values are: home, question, summary, record"/>
  <jsp:directive.attribute name="debug" required="false" description="Use wdk.debug.js"/>

  <!-- needed for wdk.debug.js -->
  <imp:siteInfo/>

  <!-- load polyfills for IE8 -->
  <![CDATA[ <!--[if lte IE 8]> ]]>

  <imp:script src="wdk/lib/json3.js"/>
  <imp:script src="wdk/lib/excanvas.min.js"/>

  <![CDATA[ <![endif]--> ]]>
  <!-- endload polyfills -->

  <c:choose>
    <c:when test="${debug eq true}">
      <!-- load files in individual script tags -->
      <imp:script charset="utf8" src="wdk/js/wdk.libs.js"/>
      <imp:script charset="utf8" src="wdk/js/wdk.js"/>
    </c:when>
    <c:otherwise>
      <!-- WDK libraries and source files -->
      <!-- see WDK/View/assets/wdkFiles.js for details about these files -->
      <imp:script charset="utf8" src="wdk/js/wdk.libs.js"/>
      <imp:script charset="utf8" src="wdk/js/wdk.js"/>
    </c:otherwise>
  </c:choose>

</jsp:root>
