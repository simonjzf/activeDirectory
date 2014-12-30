package cni.local.portal.demo;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com4j.COM4J;
import com4j.ComException;
import com4j.Variant;
import com4j.typelibs.activeDirectory.IADs;
import com4j.typelibs.ado20.ClassFactory;
import com4j.typelibs.ado20.Fields;
import com4j.typelibs.ado20._Command;
import com4j.typelibs.ado20._Connection;
import com4j.typelibs.ado20._Recordset;

public class ActiveDirectoryUserInfo {

	protected Log _log = LogFactory.getLog(ActiveDirectoryUserInfo.class);

	static String defaultNamingContext = null;
	private final Map<String, String> infoMap = new HashMap<String, String>();

	static HashMap<String, ActiveDirectoryUserInfo> knownUsers = new HashMap<String, ActiveDirectoryUserInfo>();

	synchronized void initNamingContext() {
		if (defaultNamingContext == null) {
			IADs rootDSE = COM4J.getObject(IADs.class, "LDAP://RootDSE", null);
			defaultNamingContext = (String) rootDSE.get("defaultNamingContext");
			_log.info("defaultNamingContext= " + defaultNamingContext);
		}
	}

	synchronized public static ActiveDirectoryUserInfo getInstance(String username, String requestedInfo) {
		ActiveDirectoryUserInfo found = knownUsers.get(username);
		if (found != null) {
			return found;
		}
		return getInstanceNoCache(username, requestedInfo);
	}

	synchronized public static ActiveDirectoryUserInfo getInstanceNoCache(String username, String requestedInfo) {
		ActiveDirectoryUserInfo found = new ActiveDirectoryUserInfo(username, requestedInfo);
		if (found.infoMap.isEmpty()) {
			return null;
		}
		knownUsers.put(username, found);
		return found;
	}

	private ActiveDirectoryUserInfo(String username, String requestedInfo) {
		_log.info("* ActiveDirectoryUserInfo ctor *");

		infoMap.clear();

		initNamingContext();
		if (defaultNamingContext == null) {
			return;
		}

		// Searching LDAP requires ADO [8], so it's good to create a connection
		// upfront for reuse.

		_Connection con = ClassFactory.createConnection();
		con.provider("ADsDSOObject");
		con.open("Active Directory Provider", ""/* default */, ""/* default */, -1/* default */);

		// query LDAP to find out the LDAP DN and other info for the given user
		// from the login ID

		_Command cmd = ClassFactory.createCommand();
		cmd.activeConnection(con);

		String searchField = "userPrincipalName";
		int pSlash = username.indexOf('\\');
		if (pSlash > 0) {
			searchField = "sAMAccountName";
			username = username.substring(pSlash + 1);
		}
		_log.info("sending command: " + "<LDAP://" + defaultNamingContext + ">;(" + searchField + "=" + username + ");" + requestedInfo + ";subTree");
		cmd.commandText("<LDAP://" + defaultNamingContext + ">;(" + searchField + "=" + username + ");" + requestedInfo + ";subTree");
		_Recordset rs = cmd.execute(null, Variant.getMissing(), -1/* default */);

		if (rs.eof()) { // User not found!
			_log.error(username + " not found.");
		} else {
			_log.info("got someting in RS !!");
			Fields userData = rs.fields();
			if (userData != null) {
				/*
				 * Iterator<Com4jObject> itCom = userData.iterator(); int i=0;
				 * while (itCom.hasNext()) { Com4jObject comObj = itCom.next();
				 * _log.info(i++ +":"+comObj.toString()); }
				 */

				buildInfoMap(requestedInfo, userData);

			} else {
				_log.error("User " + username + " information is empty.");
			}
		}

		if (infoMap.isEmpty()) {
			_log.error("user-info map is empty - no data was written to it.");
		}

		rs.close();
		con.close();
	}

	private void buildInfoMap(String requestedInfo, Fields userData) {
		StringTokenizer tokenizer = new StringTokenizer(requestedInfo, ",");
		String detail;
		String value = null;
		while (tokenizer.hasMoreTokens()) {
			detail = tokenizer.nextToken();

			try {
				Object o = userData.item(detail).value();
				if (o != null) {
					value = o.toString();
					_log.info(detail + " = " + value);
					infoMap.put(detail, value);
				}
			} catch (ComException ecom) {
				_log.error(detail + " not returned: " + ecom.getMessage());
			}
		}
	}

	public static String getDefaultNamingContext() {
		return defaultNamingContext;
	}

	public Map<String, String> getInfoMap() {
		return infoMap;
	}

	public static void main(String[] abc) {
		String requestedFields = "distinguishedName,userPrincipalName,telephoneNumber,mail,employeeId";
		// the fully qualified name of the user in the AD.
		// <Domain-name>\<username>
		String fqn = "CNI\\Simon.jia";
		ActiveDirectoryUserInfo userInfo = ActiveDirectoryUserInfo.getInstance(fqn, requestedFields);
		Map<String, String> infoMap = userInfo.getInfoMap();
		String email = infoMap.get("mail");
	}
}