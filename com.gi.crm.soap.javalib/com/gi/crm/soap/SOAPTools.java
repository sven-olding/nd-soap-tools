package com.gi.crm.soap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import lotus.domino.NotesException;

/**
 * Verschiedene Hilfsfunktionen für SOAP Web Services
 * 
 * @author SOL
 */
public abstract class SOAPTools
{

	/**
	 * Erzeugt eine SOAP Nachricht
	 * 
	 * @param soapAction die SOAP Action die im Header gesetzt werden soll
	 * @param xmlString die Nachricht als XML String
	 * @param username Benutzername
	 * @param password Passwort
	 * @return die SOAP Nachricht
	 * @throws NotesException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws TransformerException
	 * @throws SOAPException
	 */
	public static SOAPMessage buildMessage(String soapAction, String xmlString, String username, String password)
			throws NotesException, ParserConfigurationException, SAXException, IOException, TransformerException,
			SOAPException
	{
		// Message erzeugen
		SOAPMessage message = MessageFactory.newInstance().createMessage(null,
				new ByteArrayInputStream(xmlString.getBytes("UTF-8")));

		// Benutzer + Passwort HTTP Basic Auth hinzufügen
		String userPass = username + ":" + password;
		message.getMimeHeaders().addHeader("Authorization",
				"Basic " + Base64.getEncoder().encodeToString(userPass.getBytes()));

		// SOAP Action Header
		if (soapAction != null && !soapAction.isEmpty()) {
			message.getMimeHeaders().addHeader("SOAPAction", soapAction);
		}

		// Content Type
		message.getMimeHeaders().addHeader("Content-Type", "text/xml;charset=UTF-8");

		return message;
	}

	/**
	 * Erzeugt eine SOAP Nachricht
	 * 
	 * @param xmlString die Nachricht als XML String
	 * @param username Benutzername
	 * @param password Passwort
	 * @return die SOAP Nachricht
	 * @throws NotesException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws TransformerException
	 * @throws SOAPException
	 */
	public static SOAPMessage buildMessage(String xmlString, String username, String password) throws NotesException,
			ParserConfigurationException, SAXException, IOException, TransformerException, SOAPException
	{
		return buildMessage(null, xmlString, username, password);
	}

	/**
	 * Liefert ein XML Dokument für einen XML String
	 * 
	 * @param bodyXML der XML String
	 * @return das XML Dokument
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public static Document getDocumentFromString(String bodyXML)
			throws ParserConfigurationException, SAXException, IOException
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(bodyXML)));
	}

	/**
	 * Liefert eine SOAP Nachricht als String
	 * 
	 * @param message die SOAP Nachricht
	 * @return message als String
	 */
	public static String soapMessageToString(SOAPMessage message)
	{
		String result = null;

		if (message != null) {
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				message.writeTo(baos);
				result = new String(baos.toByteArray(), StandardCharsets.UTF_8);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	/**
	 * Versendet eine SOAP Nachricht
	 * 
	 * @param message die Nachricht
	 * @param endpoint endpoint URL
	 * @return die Antwort
	 * @throws SOAPException
	 */
	public static SOAPMessage sendSOAPMessage(SOAPMessage message, String endpoint) throws SOAPException
	{
		SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
		SOAPConnection soapConnection = soapConnectionFactory.createConnection();

		return soapConnection.call(message, endpoint);
	}

	/**
	 * Versendet eine SOAP Nachricht
	 * 
	 * @param message die Nachricht
	 * @param endpoint endpoint URL
	 * @param timeout connect Timeout in Millisekunden
	 * @return die Antwort
	 * @throws SOAPException
	 * @throws MalformedURLException
	 */
	public static SOAPMessage sendSOAPMessage(SOAPMessage message, String endpoint, final int timeout)
			throws SOAPException, MalformedURLException
	{
		SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
		SOAPConnection soapConnection = soapConnectionFactory.createConnection();

		URL url = new URL(new URL(endpoint), endpoint, new URLStreamHandler() {
			@Override
			protected URLConnection openConnection(URL url) throws IOException
			{
				URL target = new URL(url.toString());
				URLConnection connection = target.openConnection();
				connection.setConnectTimeout(timeout);
				connection.setReadTimeout(timeout);
				return connection;
			}
		});
		return soapConnection.call(message, url);
	}
}
