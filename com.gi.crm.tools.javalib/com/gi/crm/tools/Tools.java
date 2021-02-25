package com.gi.crm.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Vector;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import lotus.domino.Agent;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.WebServiceBase;

/**
 * Helper functions
 * 
 * @author SOL
 */
public abstract class Tools
{
	/**
	 * get stack trace of a given throwable as string
	 * 
	 * @param t the throwable
	 * @return the stack strace
	 */
	public static String getStackTraceAsString(Throwable t)
	{
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		String stackTrace = sw.toString();
		try {
			sw.close();
		} catch (IOException e) {
		}
		pw.close();
		return stackTrace;
	}

	/**
	 * Helper function for OPGetPath
	 * 
	 * @param aliasName database alias name
	 * @return database path as array with two elements ([0]=server,[1]=path)
	 * @throws NotesException
	 * @throws PathNotFoundException
	 */
	public static String[] getDatabasePath(String aliasName) throws NotesException, PathNotFoundException
	{
		String[] ret = new String[2];

		Document paramDoc = getSession().getCurrentDatabase().createDocument();

		paramDoc.replaceItemValue("agentValueAliasName", aliasName);

		Agent agent = getSession().getCurrentDatabase().getAgent("aaJAPIOPGetPath");
		agent.runWithDocumentContext(paramDoc);

		if (paramDoc.getItemValueString("agentValueReturnCode").equals("1")) {
			ret[0] = paramDoc.getItemValueString("agentValueServer");
			ret[1] = paramDoc.getItemValueString("agentValueFilePath");
		} else {
			throw new PathNotFoundException();
		}

		try {
			agent.recycle();
			paramDoc.recycle();
		} catch (Exception e) {
		}

		return ret;
	}

	/**
	 * Return details from throwable including stacktrace as string
	 * 
	 * @param t the throwable
	 * @return details for t
	 */
	public static String getErrorMessageFromThrowable(Throwable t)
	{
		String ret = "";
		if (t.getMessage() == null) {
			ret = t.getClass().getName();
		} else {
			ret = t.getMessage();
		}

		String stackTrace = Tools.getStackTraceAsString(t);
		if (stackTrace != null) {
			ret += "\n" + stackTrace;
		}
		return ret;
	}

	/**
	 * Creates admin process request for data sync
	 * 
	 * @param companyDoc the document to sync
	 */
	public static void createAdminRequestDataSync(Document companyDoc)
	{
		try {
			Agent agent = getSession().getCurrentDatabase().getAgent("aaJAPICreateAdminRequestDataSync");
			agent.runWithDocumentContext(companyDoc);
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Extract database name from pointer string
	 * 
	 * @param pointer the pointer (fdMe)
	 * @return the database alias name from pointer
	 */
	public static String getDbAliasFromPointer(String pointer)
	{
		return pointer.substring(pointer.lastIndexOf("|") + 1);
	}

	/**
	 * Evaluiert die Inhalte der Nodes aus der NodeList als Formeln
	 * 
	 * @param nodeList die NodeList mit den Nodes
	 * @param doc das Dokument auf das evaluiert wird
	 * @throws NotesException
	 * @throws IllegalConfigurationException
	 */
	public static void evaluateNodeContent(NodeList nodeList, Document doc) throws NotesException
	{

		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			switch (node.getNodeType()) {
				case Node.TEXT_NODE:
					String nodeValue = node.getNodeValue();

					if (nodeValue != null && !nodeValue.trim().isEmpty()) {
						String newNodeValue = null;

						Vector<?> result = null;
						if (doc != null) {
							result = SessionProvider.getSession().evaluate(nodeValue, doc);
						} else {
							result = SessionProvider.getSession().evaluate(nodeValue);
						}

						newNodeValue = (String) result.get(0);
						node.setNodeValue(newNodeValue);
					}
					break;
			}
			evaluateNodeContent(node.getChildNodes(), doc);
		}

	}

	/**
	 * Evaluiert die Inhalte der Nodes aus der NodeList als Formeln
	 * 
	 * @param nodeList die NodeList mit den Nodes
	 * @throws NotesException
	 * @throws IllegalConfigurationException
	 */
	public static void evaluateNodeContent(NodeList nodeList) throws NotesException
	{
		evaluateNodeContent(nodeList, null);
	}

	/**
	 * @param doc ein XML Dokument
	 * @return doc als String
	 * @throws TransformerException
	 */
	public static String getXmlDocumentAsString(org.w3c.dom.Document doc) throws TransformerException
	{
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		StringWriter writer = new StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(writer));
		return writer.toString();
	}

	private static Session getSession()
	{
		Session session = WebServiceBase.getCurrentSession();
		if (session == null) {
			try {
				session = NotesFactory.createSession();
			} catch (NotesException e) {
				e.printStackTrace();
			}
		}
		return session;
	}

	public static String word(String value, String delimiter, int pos)
	{
		int n = -1;
		if (value == null) {
			return null;
		}

		if (pos == 0) {
			pos = 1;
		}

		if (value.indexOf(delimiter) < 0) {
			return value;
		}

		if (pos < 0) {
			// negative values: get total num of delimiters first
			int total = 0;
			do {
				n = value.indexOf(delimiter, n + 1);
				if (n >= 0) {
					total++;
				}
			} while (n >= 0);
			pos = total + pos + 2;
			if (pos <= 0) {
				return "";
			}
		}

		do {
			n = value.indexOf(delimiter, n + 1);
			pos--;
		} while (n >= 0 && pos > 0);

		if (pos != 0) {
			return "";
		}

		if (n >= 0) {
			int i = value.lastIndexOf(delimiter, n - 1);
			if (i < 0) {
				return value.substring(0, n);
			}
			return value.substring(i + delimiter.length(), n);
		}

		n = value.lastIndexOf(delimiter);

		if (n >= 0) {
			return value.substring(n + delimiter.length(), value.length());
		}

		return "";

	} // end word()
}
