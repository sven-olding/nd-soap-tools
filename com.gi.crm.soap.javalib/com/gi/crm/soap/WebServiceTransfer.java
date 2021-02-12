package com.gi.crm.soap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPMessage;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.SAXException;

import com.gi.crm.log.SimpleLog;
import com.gi.crm.tools.SessionProvider;
import com.gi.crm.tools.Tools;
import com.gi.crm.tools.XPathParser;

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

	private Vector<String>	outputMapping			= null;
	private Vector<String>	messageTemplate			= null;

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

			serviceName = configDoc.getItemValueString("ServiceName");

			username = configDoc.getItemValueString("UserName");
			password = configDoc.getItemValueString("Password");
			soapAction = configDoc.getItemValueString("SOAPAction");
			direction = configDoc.getItemValueString("Direction");
			xpathDefaultPrefix = configDoc.getItemValueString("XPathDefaultPrefix");
			xpathDefaultNamespace = configDoc.getItemValueString("XPathDefaultNamespace");
			xpathErrorText = configDoc.getItemValueString("XPathErrorText");

			outputMapping = configDoc.getItemValue("Outputmapping");
			messageTemplate = configDoc.getItemValue("MessageTemplate");

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

	@SuppressWarnings("rawtypes")
	public String sendMessage(Document transferDoc, Document contextDoc) throws Exception
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
		log.log("Request:");
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
		if (outputMapping.isEmpty()) {
			return;
		}
		if (outputMapping.get(0).isEmpty()) {
			return;
		}
		XPathParser xPathParser = getXPathParser(responseString);

		Iterator<String> it = outputMapping.iterator();
		while (it.hasNext()) {
			String entry = it.next();
			int idx = entry.indexOf("|");
			String itemName = entry.substring(0, idx);
			String expression = entry.substring(idx + 1);
			Vector<String> result = xPathParser.evaluate(expression);
			contextDoc.replaceItemValue(itemName, result);
			transferDoc.replaceItemValue("OUT_" + itemName, result);
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
}
