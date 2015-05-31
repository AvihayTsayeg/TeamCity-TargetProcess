<%@ include file="/include.jsp"%>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<script type="text/javascript">
  BS.TargetProcessActions = {
    onInit: function() {
      var pattern = $j('#pattern').val();
      if (pattern === '') {
        $j('#pattern').val('#(\\d+)');
      }
    }
  }
</script>

<div>
  <table class="editProviderTable">
    <c:if test="${showType}">
      <tr>
        <th><label class="shortLabel">Connection Type:</label></th>
        <td>TargetProcess</td>
      </tr>
    </c:if>
    <tr>
      <th><label for="name" class="shortLabel">Display Name: <l:star/></label></th>
      <td>
        <props:textProperty name="name" maxlength="100"/>
        <span id="error_name" class="error"></span>
      </td>
    </tr>
    <tr>
      <th><label for="host" class="shortLabel">Server URL: <l:star/></label></th>
      <td>
        <props:textProperty name="host" maxlength="50"/>
        <span class="fieldExplanation" id="explanation_host">http://{server}/TargetProcess</span>
        <span id="error_account" class="error"></span>
      </td>
    </tr>
    <tr>
      <th><label for="username" class="shortLabel">Username: <l:star/></label></th>
      <td>
        <props:textProperty name="username" maxlength="100"/>
        <span id="error_username" class="error"></span>
      </td>
    </tr>
    <tr>
      <th><label for="secure:password" class="shortLabel">Password: <l:star/></label></th>
      <td>
        <props:passwordProperty name="secure:password" maxlength="100"/>
        <span id="error_secure:password" class="error"></span>
      </td>
    </tr>
    <tr>
      <th><label for="pattern" class="shortLabel">Pattern: <l:star/></label></th>
      <td>
        <props:textProperty name="pattern" maxlength="100" style="width: 16em;"/>
        <span id="error_pattern" class="error"></span>
        <span class="fieldExplanation">Use general regexp, e.g. #(\d+)<bs:help file="Issue+Tracker+Tab"/></span>
      </td>
    </tr>
  </table>
</div>

<script type="text/javascript">
  BS.TargetProcessActions.onInit();
</script>
