<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="url" value="<c:url value='/processQuestion.do?questionFullName=InternalQuestions.GeneRecordClasses_GeneRecordClassBySnapshotBasket&GeneRecordClasses_GeneRecordClassDataset_type=basket&questionSubmit=Run+Step'/>" />

<table class="basket">
  <tr>
    <td>
      <input id="refresh-basket-button" type="button" value="Refresh" onClick="showBasket();"/>
    </td>
    <td>
      <input id="empty-basket-button" type="button" value="Empty Basket" onClick="emptyBasket();"/></td>
    <td>
      <input id="make-strategy-from-basket-button" type="button" value="Save" onClick="saveBasket();"/>
    </td>
  </tr>
</table>
<div id="basketConfirmation" style="display:none">
  <form action="javascript:void(0);">
    <h2>Are you sure you want to empty the <span id="basketName"></span> basket?</h2>
    <input type="submit" value="Yes" onclick="jQuery.unblockUI();return true;" />
    <input type="submit" value="No" onclick="jQuery.unblockUI();return false;" />
  </form>
</div>
