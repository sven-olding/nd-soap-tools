import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPMessage;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.SAXException;

import com.gi.crm.log.SimpleLog;
import com.gi.crm.soap.SOAPTools;
import com.gi.crm.tools.Tools;
import com.gi.crm.tools.XPathParser;

import lotus.domino.AgentBase;
import lotus.domino.AgentContext;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class JavaAgent extends AgentBase
{
	private String			logModuleNameItem		= null;
	private String			messageTemplateItem		= null;
	private String			outputMappingItem		= null;
	private String			soapActionItem			= null;
	private Session			session					= null;
	private AgentContext	agentContext			= null;
	private Document		configProfile			= null;
	private Document		contactsDoc				= null;
	private String			user					= null;
	private String			password				= null;
	private String			endpoint				= null;
	private SimpleLog		log						= null;
	private Document		transferDoc				= null;
	private Database		contactsDb				= null;
	private View			contactsIdStructureView	= null;
	private String			xPathDefaultNSItem		= null;
	private String			xPathExprErrorTextItem	= null;

	@Override
	public void NotesMain()
	{
		String errMsg = null;

		try {
			session = getSession();
			agentContext = session.getAgentContext();

			configProfile = session.getCurrentDatabase().getProfileDocument("gesetkeywords", "web services");

			user = configProfile.getItemValueString("UserName");
			password = configProfile.getItemValueString("Password");
			endpoint = configProfile.getItemValueString("EndpointURL");

			String paraNoteId = agentContext.getCurrentAgent().getParameterDocID();
			if(paraNoteId!=null && !paraNoteId.isEmpty()) {
				transferDoc = agentContext.getCurrentDatabase().getDocumentByID(paraNoteId);
			} else {
				transferDoc = agentContext.getDocumentContext();
			}							
			transferDoc.replaceItemValue("agentValueReturnCode", "0");

			String fdMe = transferDoc.getItemValueString("CTX_fdMe");
			String[] fdMeSplit = fdMe.split("\\|");
			String dbName = fdMeSplit[2];
			String docType = fdMeSplit[1];

			if (docType.equalsIgnoreCase("CProfile")) {
				logModuleNameItem = "Transfer Contact to IFS";
				messageTemplateItem = "MessageTemplateReceiveContact";
				outputMappingItem = "OutputMappingReceiveContact";
				soapActionItem = "SOAPActionReceiveContact";
				xPathExprErrorTextItem = "XPathErrorTextReceiveContact";
				xPathDefaultNSItem = "XPathDefaultNSReceiveContact";
			} else if (docType.equalsIgnoreCase("Company")) {
				logModuleNameItem = "Transfer Company to IFS";
				messageTemplateItem = "MessageTemplateReceiveBusinessObject";
				outputMappingItem = "OutputMappingReceiveBusinessObject";
				soapActionItem = "SOAPActionReceiveBusinessObject";
				xPathExprErrorTextItem = "XPathErrorTextReceiveBusinessObject";
				xPathDefaultNSItem = "XPathDefaultNSReceiveBusinessObject";
			}

			initLog();

			log.log("MessageID: " + transferDoc.getItemValueString("MessageID"));
			log.log("fdMe: " + fdMe);

			if (docType.equalsIgnoreCase("CProfile") || docType.equalsIgnoreCase("Company")) {

				String path[] = Tools.getDatabasePath(dbName);

				contactsDb = session.getDatabase(path[0], path[1], false);
				contactsIdStructureView = contactsDb.getView("vaCEIDStructure");
				contactsIdStructureView.refresh();

				contactsDoc = contactsIdStructureView.getDocumentByKey(fdMe.split("\\|")[0], true);
				if (contactsDoc == null) {
					errMsg = "CRM Dokument nicht gefunden!";
					log.log(errMsg);
					transferDoc.replaceItemValue("Error", errMsg);
					transferDoc.replaceItemValue("Status", "Error");
				} else {
					String responseString = sendMessage();
					log.log("Response:");
					log.log(responseString);

					if (responseString.isEmpty()) {

						transferDoc.replaceItemValue("agentValueReturnCode", "1");

					} else {
						errMsg = getErrorMessageFromResponse(responseString);
						if (!errMsg.isEmpty()) {
							log.logError(errMsg);
							transferDoc.replaceItemValue("Error", errMsg);
							transferDoc.replaceItemValue("Status", "Error");
						} else {
							outputMapping(responseString);

							transferDoc.replaceItemValue("Error", "");
							transferDoc.replaceItemValue("Status", "Completed");

							transferDoc.replaceItemValue("agentValueReturnCode", "1");

							Tools.createAdminRequestDataSync(contactsDoc);
						}
					}
				}
			} else {
				log.logWarning("UNKNOWN DOCTYPE: " + docType);
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.logException(e);
			errMsg = Tools.getStackTraceAsString(e);
			if (transferDoc != null) {
				try {
					transferDoc.replaceItemValue("Error", errMsg);
					if (e.getMessage().toLowerCase().contains("connection refused")
							|| e.getMessage().toLowerCase().contains("connection timed out")
							|| errMsg.contains("SSLHandshakeException")) {
						transferDoc.replaceItemValue("Status", "Pending");
						transferDoc.replaceItemValue("agentValueReturnCode", "1");
					} else {
						transferDoc.replaceItemValue("Status", "Error");
					}
				} catch (NotesException e1) {
					e1.printStackTrace();
				}
			}
		} finally {
			if (transferDoc != null) {
				try {
					transferDoc.save();
					transferDoc.recycle();
				} catch (NotesException e) {
					e.printStackTrace();
				}
			}
			if (contactsDoc != null) {
				try {
					contactsDoc.recycle();
				} catch (NotesException e) {
				}
			}
			if (contactsIdStructureView != null) {
				try {
					contactsIdStructureView.recycle();
				} catch (NotesException e) {
				}
			}

			if (contactsDb != null) {
				try {
					contactsDb.recycle();
				} catch (NotesException e) {
				}
			}

			closeLog();
		}
	}

	private String getErrorMessageFromResponse(String responseString) throws UnsupportedEncodingException,
			ParserConfigurationException, SAXException, IOException, XPathExpressionException, NotesException
	{
		XPathParser xPathParser = getXPathParser(responseString);
		Vector<String> errMsg = xPathParser.evaluate(configProfile.getItemValueString(xPathExprErrorTextItem));
		if (errMsg.isEmpty()) {
			return "";
		}
		String ret = "";
		for (String s : errMsg) {
			if (ret.isEmpty()) {
				ret = s;
			} else {
				ret += "\n" + s;
			}
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	private void outputMapping(String responseString) throws NotesException, UnsupportedEncodingException,
			ParserConfigurationException, SAXException, IOException, XPathExpressionException
	{
		Vector<String> mappingEntries = configProfile.getItemValue(outputMappingItem);
		if (mappingEntries.isEmpty()) {
			return;
		}
		if (mappingEntries.get(0).isEmpty()) {
			return;
		}
		XPathParser xPathParser = getXPathParser(responseString);

		Iterator<String> it = mappingEntries.iterator();
		while (it.hasNext()) {
			String entry = it.next();
			int idx = entry.indexOf("|");
			String itemName = entry.substring(0, idx);
			String expression = entry.substring(idx + 1);
			Vector<String> result = xPathParser.evaluate(expression);
			contactsDoc.replaceItemValue(itemName, result);
			transferDoc.replaceItemValue("OUT_" + itemName, result);
		}
		contactsDoc.replaceItemValue("fl_IFS_OutputMapping", "1");		
		contactsDoc.replaceItemValue("tmpAccountTypeChanged", "");
		contactsDoc.save();
	}

	private XPathParser getXPathParser(String responseString)
			throws UnsupportedEncodingException, NotesException, SAXException, IOException, ParserConfigurationException
	{
		return new XPathParser(responseString, configProfile.getItemValueString("XPathDefaultNSPrefix"),
				configProfile.getItemValueString(xPathDefaultNSItem));
	}

	@SuppressWarnings("rawtypes")
	private String sendMessage() throws Exception
	{
		String template = "";
		Vector value = configProfile.getItemValue(messageTemplateItem);
		Iterator it = value.iterator();
		while (it.hasNext()) {
			template += it.next() + "\n";
		}

		org.w3c.dom.Document xmlDoc = SOAPTools.getDocumentFromString(template);
		Tools.evaluateNodeContent(xmlDoc.getChildNodes(), contactsDoc);

		String action = configProfile.getItemValueString(soapActionItem);
		String messageString = Tools.getXmlDocumentAsString(xmlDoc);

		log.log("SOAP Action: " + action);
		log.log("Request:");
		log.log(messageString);
		SOAPMessage message = SOAPTools.buildMessage(action, messageString, user, password);

		SOAPMessage response = null;
		try {
			response = SOAPTools.sendSOAPMessage(message, endpoint);
		} catch (Exception e) {
			if (e.getCause() != null && e.getCause().getMessage().equalsIgnoreCase("message send failed")) {
				if (e.getCause().getCause() != null && (e.getCause().getCause() instanceof SocketTimeoutException
						|| e.getCause().getCause() instanceof ConnectException)) {
					transferDoc.replaceItemValue("Status", "PENDING");

				} else {
					throw e;
				}
			} else {
				throw e;
			}
		}
		if (response == null) {
			return "";
		}
		return SOAPTools.soapMessageToString(response);
	}

	private void closeLog()
	{
		if (log != null) {
			log.addNewLine();
			log.log("ENDE Transfer");
			log.close();
		}
	}

	private void initLog() throws NotesException
	{
		log = new SimpleLog(session.getCurrentDatabase(), logModuleNameItem, session.getCurrentDatabase().getTitle(),
				session.getEffectiveUserName());

		log.log("START Transfer");

		log.log("User: " + user);
		log.log("Endpoint: " + endpoint);
		log.addNewLine();
	}

}