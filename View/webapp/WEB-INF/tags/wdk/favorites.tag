<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<c:set var="wdkUser" value="${sessionScope.wdkUser}"/>
<c:set var="allFavorites" value="${wdkUser.favorites}" /><%-- a map of (RecordClass, List<Favorite>) --%>

<span class ="h3left">My Favorites</span><br><br>

<c:choose>
    <c:when test="${fn:length(allFavorites) == 0}">
        <p>Your favorites page is currently empty.  To add items to your favorites simply click on the favorites icon in record pages (i.e. gene or isolate pages)</p>
    </c:when>
    <c:otherwise> <%-- has favorites --%>

            <ul class="menubar">
              <c:forEach var="fav_item" items="${allFavorites}">
                <c:set var="recordClass" value="${fav_item.key}" />
                <c:set var="favorites" value="${fav_item.value}" /> <%-- a list of favorites of a record type --%>
		<c:set var="idTag" value="${fn:replace(recordClass.fullName, '.', '_')}" />
                <li>
                  <a id="tab_${idTag}" href="javascript:void(0)" onclick="showFavorites('${idTag}')">${recordClass.type}s (${fn:length(favorites)})</a>
                </li>
              </c:forEach>
            </ul>


<table width="100%">
<tr>
<td width="50%" style="padding:0">
	<input class="favorite-refresh-button" style="margin-left:10px;cursor:pointer" title="Reload the page after you click on the star to add/remove IDs." type="button" value="Reload page" onclick="window.location.reload();"/>
 	<div style="clear:both;font-style:italic;font-size:90%;padding-left:10px;" >(Mouse over column headings for help)</div>
</td>
<td width="50%" style="text-align:right;padding:0">
	<p style="color:darkred;font-size:95%;font-style:italic"><b>Note on new releases:</b> 
	Annotation changes might make IDs in your favorites outdated. 
	<br>Click on any ID to access the new ID's page. Unmapped IDs will show a message.</p>
</td>
</tr>
</table>

            <c:forEach var="fav_item" items="${allFavorites}">
              <c:set var="recordClass" value="${fav_item.key}" />
	      <c:set var="idTag" value="${fn:replace(recordClass.fullName, '.', '_')}" /> 
              <div id="favorites_${idTag}" class="favorites_panel">
                <c:set var="favorites" value="${fav_item.value}" /> <%-- a list of favorites of a record type --%>


                <table class="favorite-list mytableStyle" width="100%">
                    <tr>
			<th title="Click on the star to remove an ID from Favorites. It will not be removed from this page until you hit 'Refresh' or reload the page." class="mythStyle clickable">${recordClass.type}s</th>
			<th title="Use this column to add notes (click Edit to change this field). Initially it contains the product name associated with the ID."  class="mythStyle clickable">Notes</th>
			<th title="Organize your favorites by project names. Click Edit to add/change it; IDs with the same project name will be sorted together once the page is refreshed."  class="mythStyle clickable">Project</th>
		    </tr>


                    <c:forEach var="favorite" items="${favorites}">
                        <c:set var="record" value="${favorite.recordInstance}" />
                        <c:set var="basketColor" value="gray"/>
						<c:set var="basketValue" value="0"/>
						<c:if test="${record.inBasket}">
							<c:set var="basketColor" value="color"/>
							<c:set var="basketValue" value="1"/>
						</c:if>
						<c:set var="primaryKey" value="${record.primaryKey}"/>
                        <c:set var="pkValues" value="${primaryKey.values}" />
                        <c:set value="${pkValues['source_id']}" var="id"/>
                        <c:set value="${pkValues['project_id']}" var="pid"/>
                        <tr class="wdk-record" recordClass="${recordClass.fullName}">
                            <td width="10%" class="mytdStyle" nowrap>
                                <span class="primaryKey">
                                    <c:forEach var="pk_item" items="${pkValues}">
                                        <span key="${pk_item.key}">${pk_item.value}</span>
                                    </c:forEach>
                                </span>
                                <img class="clickable" src="<c:url value='/wdk/images/favorite_color.gif'/>" 
                                     title="Click to remove this item from favorites and reload page"
				     height="16px" style="vertical-align:text-bottom"
                                     onClick="updateFavorite(this, 'remove')"/>&nbsp;
                                <img class="clickable" src="<c:url value='/wdk/images/basket_${basketColor}.png'/>" 
                                     title="Click to add/remove this item from your basket."
				     height="16px"  style="vertical-align:text-bottom"
                                     onClick="updateBasket(this,'recordPage', '${id}', '${pid}', '${recordClass.fullName}')" value="${basketValue}"/>&nbsp;

<%-- TO REMOVE AFTER RELEASE BUILD14
<c:choose>
  <c:when test="${recordClass.type == 'Gene'}" >    
	<c:set var="url" value="/processQuestion.do?questionFullName=GeneQuestions.GeneBySingleLocusTag&questionSubmit=Get+Answer&value%28single_gene_id%29=${id}" />  
  </c:when>
  <c:when test="${recordClass.type == 'Isolate'}">
	<c:set var="url" value="/processQuestion.do?questionFullName=IsolateQuestions.IsolateByIsolateId&questionSubmit=Get+Answer&isolate_id_type=data&isolate_id_data=${id}" />  
  </c:when>
  <c:when test="${recordClass.type == 'Genomic Sequence'}">
	<c:set var="url" value="/processQuestion.do?questionFullName=GenomicSequenceQuestions.SequenceBySourceId&questionSubmit=Get+Answer&sequenceId_type=data&sequenceId_data=${id}" />  
  </c:when>
  <c:when test="${recordClass.type == 'SNP'}">
	<c:set var="url" value="/processQuestion.do?questionFullName=SnpQuestions.SnpBySourceId&questionSubmit=Get+Answer&snp_id_type=data&snp_id_data=${id}" />  
  </c:when>
  <c:when test="${recordClass.type == 'EST'}">
	<c:set var="url" value="/processQuestion.do?questionFullName=EstQuestions.EstBySourceId&questionSubmit=Get+Answer&est_id_type=data&est_id_data=${id}" />  
  </c:when>
  <c:when test="${recordClass.type == 'ORF'}">
	<c:set var="url" value="/processQuestion.do?questionFullName=OrfQuestions.OrfByOrfId&questionSubmit=Get+Answer&orf_id_type=data&orf_id_data=${id}" />  
  </c:when>
  <c:when test="${recordClass.type == 'SAGE Tag Alignment'}">
	<c:set var="url" value="/processQuestion.do?questionFullName=SageTagQuestions.SageTagByRadSourceId&questionSubmit=Get+Answer&rad_source_id_type=data&rad_source_id_data=${id}" />  
  </c:when>
  <c:otherwise>
--%>
    <c:set var="url" value="/showRecord.do?name=${recordClass.fullName}&source_id=${id}" />
<%--
  </c:otherwise>
</c:choose>
--%>

                          <%--      <c:forEach var="pk_item" items="${pkValues}">
                                    <c:set var="url" value="${url}&${pk_item.key}=${pk_item.value}" />
                                </c:forEach>  --%>

                                <a title="Click to access this ID's page" href="<c:url value='${url}' />">${primaryKey.value}</a>
                            </td>
                            <td width="60%"  class="mytdStyle" >
								<c:set var="favNote" value="${favorite.note}"/>
                                <span class="favorite-note">${favNote}</span>
                                <div class="favorite-button-div"><a href="javascript:void(0)" class="favorite-note-button" onClick="showInputBox(this, 'note', 'updateFavoriteNote(this)')" >edit</a></div>
                            </td>
                            <td width="30%"  class="mytdStyle" >
								<c:set var="favGroup" value="${favorite.group}"/>
								<c:set var="favGroupStyle" value=""/>
								<c:if test="${fn:length(favGroup) == 0}">
									<c:set var="favGroup" value="Click edit to add a project"/>
									<c:set var="favGroupStyle" value="opacity:0.2"/>
								</c:if>
                                <span class="favorite-group" style="${favGroupStyle}">${favGroup}</span>
                                <a href="javascript:void(0)" class="favorite-group-button" onClick="showInputBox(this, 'group', 'updateFavoriteGroup(this)')">edit</a>
                            </td>
                        </tr>
                    </c:forEach>
                </table>
               </div>
             </c:forEach>
    </c:otherwise> <%-- END has favorites --%>
</c:choose>
