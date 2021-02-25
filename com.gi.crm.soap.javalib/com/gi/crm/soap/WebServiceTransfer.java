package com.gi.crm.soap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPMessage;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.gi.crm.log.SimpleLog;
import com.gi.crm.tools.PathNotFoundException;
import com.gi.crm.tools.SessionProvider;
import com.gi.crm.tools.Tools;
import com.gi.crm.tools.XPathParser;

import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class WebServiceTransfer
{
	private String			serviceName				= "";
	private SimpleLog		log						= null;
	private String			username				= "";
	private String			password				= "";
	private String			soapAction				= "";
	private String			endpoint				= "";
	private String			direction				= "";
	private String			xpathDefaultPrefix		= "";
	private String			xpathDefaultNamespace	= "";
	private String			xpathErrorText			= "";
	private String			targetServer			= "";
	private String			targetDatabase			= "";
	private String			targetView				= "";
	private String			xpathKey				= "";
	private String			xpathItem				= "";
	private boolean			createNewDocs			= false;
	private boolean			computeWithForm			= false;
	private boolean			createDataSyncRequest	= false;
	private Vector<String>	outputMappingXPath		= null;
	private Vector<String>	outputMappingFormula	= null;
	private Vector<String>	formulaNewDocs			= null;
	private Vector<String>	messageTemplate			= null;
	private Database		dbSet					= null;

	public WebServiceTransfer(String serviceName, SimpleLog log)
	{
		this.serviceName = serviceName;
		this.log = log;
		loadServiceDefinition();
	}

	@SuppressWarnings("unchecked")
	private void loadServiceDefinition()
	{
		Session session = SessionProvider.getSession();
		View configView = null;
		Document configDoc = null;
		try {
			configView = session.getCurrentDatabase().getView("($ServiceDefinitions)");
			configDoc = configView.getDocumentByKey(serviceName, true);

			endpoint = configDoc.getItemValueString("Endpoint");
			username = configDoc.getItemValueString("UserName");
			password = configDoc.getItemValueString("Password");
			soapAction = configDoc.getItemValueString("SOAPAction");
			direction = configDoc.getItemValueString("Direction");
			xpathDefaultPrefix = configDoc.getItemValueString("XPathDefaultPrefix");
			xpathDefaultNamespace = configDoc.getItemValueString("XPathDefaultNamespace");
			xpathErrorText = configDoc.getItemValueString("XPathErrorText");
			direction = configDoc.getItemValueString("Direction");
			outputMappingXPath = configDoc.getItemValue("Outputmapping");
			outputMappingFormula = configDoc.getItemValue("OutputmappingFormula");
			messageTemplate = configDoc.getItemValue("MessageTemplate");
			targetServer = configDoc.getItemValueString("TargetServer");
			targetDatabase = configDoc.getItemValueString("TargetDb");
			targetView = configDoc.getItemValueString("TargetView");
			xpathItem = configDoc.getItemValueString("XPathItem");
			xpathKey = configDoc.getItemValueString("XPathKeyField");
			formulaNewDocs = configDoc.getItemValue("FormulaNewDocs");
			createNewDocs = configDoc.getItemValueString("CreateNew").equals("1");
			computeWithForm = configDoc.getItemValueString("ComputeWithForm").equals("1");
			createDataSyncRequest = configDoc.getItemValueString("CreateDataSyncAdminR").equals("1");

		} catch (NotesException e) {
			e.printStackTrace();
			log.logException(e);
		} finally {
			try {
				configDoc.recycle();
			} catch (NotesException e) {
				e.printStackTrace();
			}

			try {
				configView.recycle();
			} catch (NotesException e) {
				e.printStackTrace();
			}
		}

	}

	public void pullData()
	{
		Stack<Base> recycleList = new Stack<Base>();
		Document transferDoc = null;

		try {
			transferDoc = createNewTransferDoc();
			recycleList.add(transferDoc);

			Database targetDb = SessionProvider.getSession().getDatabase(targetServer, targetDatabase);
			recycleList.add(targetDb);
			View targetView = targetDb.getView(this.targetView);
			recycleList.add(targetView);
			targetView.refresh();

			String response = sendMessage();
			transferDoc.replaceItemValue("Data", response);
			log.log("Response\n" + response);

			XPathParser xPathParser = getXPathParser(response);
			log.log("XPath item expression: " + xpathItem);

			NodeList nodeList = xPathParser.evaluateToNodeList(xpathItem);
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				String nodeXml = Tools.nodeToString(node);

				XPathParser itemParser = new XPathParser(nodeXml);
				Vector<String> key = itemParser.evaluate(xpathKey);
				log.log("Key: " + key.toString());
				Document contextDoc = targetView.getDocumentByKey(key, true);
				if (contextDoc == null && createNewDocs) {
					log.log("No document found, creating new");
					contextDoc = createNewDoc(targetDb);
					log.log("New doc created: " + contextDoc.getItemValueString("fdMe") + " ("
							+ contextDoc.getUniversalID() + ")");
				}
				if (contextDoc == null) {
					log.log("No document found and option to create new docs is set to false");
				} else {
					outputMapping(nodeXml, transferDoc, contextDoc);
					contextDoc.save();

					if (createDataSyncRequest) {
						HashMap<String, Object> params = new HashMap<>();
						params.put("fdParam1", contextDoc.getItemValueString("ID"));
						createNewAdminRequestDoc("DataSync", contextDoc, params);
					}
					try {
						contextDoc.recycle();
					} catch (NotesException e) {
						// ignore
					}
				}
			}

			transferDoc.replaceItemValue("Status", "COMPLETED");
			transferDoc.replaceItemValue("Error", "");
		} catch (Exception e) {
			e.printStackTrace();
			log.logException(e);

			if (transferDoc != null) {
				try {
					transferDoc.replaceItemValue("Status", "Error");
					transferDoc.replaceItemValue("Error", Tools.getStackTraceAsString(e));
				} catch (NotesException e1) {
					e1.printStackTrace();
				}
			}

		} finally {
			if (transferDoc != null) {
				try {
					transferDoc.save();
				} catch (NotesException e) {
					e.printStackTrace();
				}
			}

			for (Base o : recycleList) {
				try {
					if (o != null) {
						o.recycle();
					}
				} catch (NotesException e) {
					// ignore
				}
			}

		}
	}

	private void createNewAdminRequestDoc(String requestType, Document contextDoc, HashMap<String, Object> params)
			throws NotesException
	{
		Document newDoc = getDbSet().createDocument();

		Database parentDatabase = contextDoc.getParentDatabase();
		Document dbprofile = parentDatabase.getProfileDocument("gesetdatabase", null);

		newDoc.replaceItemValue("Form", "faAdminRequest");
		newDoc.replaceItemValue("DocType", "faAdminRequest");
		newDoc.replaceItemValue("fdDbName", dbprofile.getItemValueString("fdDbName"));
		newDoc.replaceItemValue("fdDbServer", parentDatabase.getServer());
		newDoc.replaceItemValue("fdDbTitle", parentDatabase.getTitle());
		newDoc.replaceItemValue("fdForce", "1");
		newDoc.replaceItemValue("fdIsNewDoc", "0");
		newDoc.replaceItemValue("fdLogName", dbprofile.getItemValueString("fdDbName"));
		newDoc.replaceItemValue("fdRequestType", requestType);
		newDoc.replaceItemValue("fdRequestStatus", "0");
		newDoc.replaceItemValue("fdSyncEnvironment", dbprofile.getItemValueString("fdSyncEnvironment"));

		params.forEach((key, value) -> {
			try {
				newDoc.replaceItemValue(key, value);
			} catch (NotesException e) {
				e.printStackTrace();
				log.logException(e);
			}
		});

		newDoc.computeWithForm(false, false);
		newDoc.save();
	}

	private Document createNewTransferDoc() throws NotesException
	{
		Document newDoc = SessionProvider.getSession().getCurrentDatabase().createDocument();
		newDoc.replaceItemValue("Form", "faTransfer");
		newDoc.replaceItemValue("TransferDirection", "Incoming");
		newDoc.replaceItemValue("Status", "New");
		newDoc.replaceItemValue("TransferType", serviceName);
		newDoc.replaceItemValue("UserName", SessionProvider.getSession().getEffectiveUserName());

		newDoc.computeWithForm(false, false);
		return newDoc;
	}

	private Document createNewDoc(Database db) throws NotesException
	{
		Document newDoc = db.createDocument();
		Iterator<String> it = formulaNewDocs.iterator();
		while (it.hasNext()) {
			String entry = it.next();
			int idx = entry.indexOf("|");
			String itemName = entry.substring(0, idx);
			String expression = entry.substring(idx + 1);
			Vector<?> result = SessionProvider.getSession().evaluate(expression, newDoc);
			newDoc.replaceItemValue(itemName, result);
		}
		newDoc.computeWithForm(false, false);

		return newDoc;
	}

	public String sendMessage() throws Exception
	{
		return sendMessage(null);
	}

	@SuppressWarnings("rawtypes")
	public String sendMessage(Document contextDoc) throws Exception
	{
		log.log("Sending message...");
		log.log("Endpoint: " + endpoint);
		log.log("User: " + username);

		String template = "";
		Iterator it = messageTemplate.iterator();
		while (it.hasNext()) {
			template += it.next() + "\n";
		}

		org.w3c.dom.Document xmlDoc = SOAPTools.getDocumentFromString(template);
		Tools.evaluateNodeContent(xmlDoc.getChildNodes(), contextDoc);

		String messageString = Tools.getXmlDocumentAsString(xmlDoc);

		log.log("SOAP Action: " + soapAction);
		log.log("Request:\n");
		log.log(messageString);
		SOAPMessage message = SOAPTools.buildMessage(soapAction, messageString, username, password);

		SOAPMessage response = SOAPTools.sendSOAPMessage(message, endpoint);

		if (response == null) {
			return "";
		}
		return SOAPTools.soapMessageToString(response);
	}

	public void outputMapping(String responseString, Document transferDoc, Document contextDoc)
			throws NotesException, UnsupportedEncodingException, ParserConfigurationException, SAXException,
			IOException, XPathExpressionException
	{
		if (outputMappingXPath.isEmpty() && outputMappingFormula.isEmpty()) {
			return;
		}
		if (outputMappingXPath.get(0).isEmpty() && outputMappingFormula.get(0).isEmpty()) {
			return;
		}

		// XPath expressions
		XPathParser xPathParser = getXPathParser(responseString);
		Iterator<String> it = outputMappingXPath.iterator();
		while (it.hasNext()) {
			String entry = it.next();
			int idx = entry.indexOf("|");
			String itemName = entry.substring(0, idx);
			String expression = entry.substring(idx + 1);
			Vector<String> result = xPathParser.evaluate(expression);
			contextDoc.replaceItemValue(itemName, result);
			if (direction == "push") {
				transferDoc.replaceItemValue("OUT_" + itemName, result);
			}
		}

		if (computeWithForm) {
			contextDoc.computeWithForm(false, false);
		}

		// @Formula
		it = outputMappingFormula.iterator();
		while (it.hasNext()) {
			String entry = it.next();
			int idx = entry.indexOf("|");
			String itemName = entry.substring(0, idx);
			String expression = entry.substring(idx + 1);
			Vector<?> result = SessionProvider.getSession().evaluate(expression, contextDoc);
			contextDoc.replaceItemValue(itemName, result);
			if (direction == "push") {
				transferDoc.replaceItemValue("OUT_" + itemName, result);
			}
		}

		if (computeWithForm) {
			contextDoc.computeWithForm(false, false);
		}
	}

	private XPathParser getXPathParser(String xml)
			throws UnsupportedEncodingException, NotesException, SAXException, IOException, ParserConfigurationException
	{
		return new XPathParser(xml, xpathDefaultPrefix, xpathDefaultNamespace);
	}

	public String getErrorMessageFromResponse(String responseString) throws UnsupportedEncodingException,
			ParserConfigurationException, SAXException, IOException, XPathExpressionException, NotesException
	{
		if (xpathErrorText.isEmpty()) {
			return "";
		}

		XPathParser xPathParser = getXPathParser(responseString);
		Vector<String> errMsg = xPathParser.evaluate(xpathErrorText);
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

	private Database getDbSet()
	{
		if (dbSet == null) {
			try {
				String[] path = Tools.getDatabasePath("GeDBSettings");
				dbSet = SessionProvider.getSession().getDatabase(path[0], path[1], false);
			} catch (NotesException e) {
				e.printStackTrace();
				log.logException(e);
			} catch (PathNotFoundException e) {
				e.printStackTrace();
			}
		}
		return dbSet;
	}
}
