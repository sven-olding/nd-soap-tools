package com.gi.crm.log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

import com.gi.crm.EncodeTools;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.MIMEEntity;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.Stream;

/**
 * Class for simple logging to CELog documents
 * 
 * @author SOL
 */
public class SimpleLog implements Serializable, lotus.domino.Base
{

	/**
	 * 
	 */
	private static final long	serialVersionUID	= -9644723147851L;

	/** The notes database */
	private Database			targetDb			= null;

	/** the parent session */
	private Session				session				= null;

	/** The document to log into */
	private Document			logDoc				= null;

	/** The inner MIME Entity (Body field) to log messages to */
	private MIMEEntity			mimeEntity			= null;

	/** stream with messages (HTML string) */
	private Stream				stream				= null;

	/** name of the event, log title */
	private String				event				= null;

	/** Name of the database in which events occur */
	private String				sourceDb			= null;

	/** User name for event */
	private String				user				= null;

	/** counter for all entries written to log */
	private long				entryCount			= 0;

	/** counter for all warnings written to log */
	private long				warningCount		= 0;

	/** counter for all errors written to log */
	private long				errorCount			= 0;

	/** flag indicating if log document was already created */
	private boolean				initialized			= false;

	/** flag indicating if log already was closed */
	private boolean				closed				= false;

	/**
	 * @return the event
	 */
	public String getEvent()
	{
		return event;
	}

	/**
	 * @param event the event to set
	 */
	public void setEvent(String event)
	{
		this.event = event;
	}

	/**
	 * @return the sourceDb
	 */
	public String getSourceDb()
	{
		return sourceDb;
	}

	/**
	 * @param sourceDb the sourceDb to set
	 */
	public void setSourceDb(String sourceDb)
	{
		this.sourceDb = sourceDb;
	}

	/**
	 * @return the user
	 */
	public String getUser()
	{
		return user;
	}

	/**
	 * @param user the user to set
	 */
	public void setUser(String user)
	{
		this.user = user;
	}

	/**
	 * @return the entryCount
	 */
	public long getEntryCount()
	{
		return entryCount;
	}

	/**
	 * @param entryCount the entryCount to set
	 */
	public void setEntryCount(long entryCount)
	{
		this.entryCount = entryCount;
	}

	/**
	 * @return the warningCount
	 */
	public long getWarningCount()
	{
		return warningCount;
	}

	/**
	 * @param warningCount the warningCount to set
	 */
	public void setWarningCount(long warningCount)
	{
		this.warningCount = warningCount;
	}

	/**
	 * @return the errorCount
	 */
	public long getErrorCount()
	{
		return errorCount;
	}

	/**
	 * @param errorCount the errorCount to set
	 */
	public void setErrorCount(long errorCount)
	{
		this.errorCount = errorCount;
	}

	/**
	 * @param initialized the initialized to set
	 */
	public void setInitialized(boolean initialized)
	{
		this.initialized = initialized;
	}

	/**
	 * @return the initialized
	 */
	public boolean isInitialized()
	{
		return initialized;
	}

	/**
	 * @param closed the closed to set
	 */
	public void setClosed(boolean closed)
	{
		this.closed = closed;
	}

	/**
	 * @return the closed
	 */
	public boolean isClosed()
	{
		return closed;
	}

	/**
	 * Constructor
	 * 
	 * @param targetDb - the database to log into
	 * @param event - name of the event
	 * @param sourceDb - originating db
	 * @param user - username
	 * @throws NotesException
	 */
	public SimpleLog(Database targetDb, String event, String sourceDb, String user) throws NotesException
	{
		this.session = targetDb.getParent();
		this.targetDb = targetDb;
		this.event = event;
		this.sourceDb = sourceDb;
		this.user = user;
	}

	/**
	 * initialize the log document and more internal stuff
	 * 
	 * @throws NotesException
	 */
	private void initialize() throws NotesException
	{
		if (isInitialized())
			return;

		logDoc = targetDb.createDocument();

		logDoc.replaceItemValue("Form", "faCELog");
		logDoc.replaceItemValue("fdLogDate", session.createDateTime(new Date()));
		logDoc.replaceItemValue("fdLogEvent", event);
		logDoc.replaceItemValue("fdUser", user);
		logDoc.replaceItemValue("fdLogNumber", 1);
		logDoc.replaceItemValue("fdDBName", sourceDb);

		mimeEntity = logDoc.createMIMEEntity("fdLogEntrys");
		stream = session.createStream();

		setInitialized(true);
	}

	/**
	 * Write the log to target db
	 */
	public void close()
	{
		if (!isInitialized() || isClosed())
			return;

		try {
			if (mimeEntity != null && stream != null) {
				mimeEntity.setContentFromText(stream, "text/html;charset=UTF-8", MIMEEntity.ENC_IDENTITY_8BIT);
				stream.close();
			}
			logDoc.replaceItemValue("fdLogErrors", getErrorCount());
			logDoc.replaceItemValue("fdLogWarnings", getWarningCount());
			logDoc.closeMIMEEntities(true);
			logDoc.computeWithForm(false, false);
			logDoc.save();
		} catch (NotesException e) {
		} finally {
			try {
				logDoc.recycle();
				setClosed(true);
			} catch (NotesException e) {
			}
		}
	}

	/**
	 * add new lines to log
	 * 
	 * @param count amount of new lines
	 */
	public void addNewLine(int count)
	{
		if (!isInitialized()) {
			try {
				initialize();
			} catch (NotesException e) {
				e.printStackTrace();
				System.out.println(event + " (" + user + "): " + e.getMessage());
			}
		}

		for (int i = 0; i < count; i++) {
			try {
				stream.writeText("<br />");
			} catch (NotesException e) {
			}
		}
	}

	/**
	 * add empty new line to log
	 */
	public void addNewLine()
	{
		addNewLine(1);
	}

	/**
	 * Log as plain text message
	 * 
	 * @param msg
	 */
	public void log(String msg)
	{
		if (isClosed())
			return;

		if (!isInitialized()) {
			try {
				initialize();
			} catch (NotesException e) {
				e.printStackTrace();
				System.out.println(event + " (" + user + "): " + msg);
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<div style=\"font-family: Arial; font-size: 10px; margin-left: 5px; margin-right: 5px\">");
		sb.append(getTimestampPrefix());
		String logMsg = EncodeTools.forHtmlContent(msg);
		sb.append(logMsg);
		sb.append("<br />");
		sb.append("</div>");

		try {
			stream.writeText(sb.toString());
		} catch (NotesException e) {
			e.printStackTrace();
			System.out.println(event + " (" + user + "): " + msg);
		}

		entryCount++;
	}

	/**
	 * Log as warning message
	 * 
	 * @param msg
	 */
	public void logWarning(String msg)
	{
		if (isClosed())
			return;

		if (!isInitialized()) {
			try {
				initialize();
			} catch (NotesException e) {
				e.printStackTrace();
				System.out.println(event + " (" + user + "): " + msg);
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(
				"<div style=\"font-family: Arial; font-size: 10px; font-weight: bold; color: #000099;  margin-left: 5px; margin-right: 5px\">");
		sb.append(getTimestampPrefix());
		sb.append(EncodeTools.forHtmlContent(msg));
		sb.append("<br />");
		sb.append("</div>");

		try {
			stream.writeText(sb.toString());
		} catch (NotesException e) {
			e.printStackTrace();
			System.out.println(event + " (" + user + "): " + msg);
		}

		warningCount++;
	}

	/**
	 * Log as error message
	 * 
	 * @param msg
	 */
	public void logError(String msg)
	{
		if (isClosed())
			return;

		if (!isInitialized()) {
			try {
				initialize();
			} catch (NotesException e) {
				e.printStackTrace();
				System.out.println(event + " (" + user + "): " + msg);
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(
				"<div style=\"font-family: Arial; font-size: 10px; font-weight: bold; color: #ff0000; margin-left: 5px; margin-right: 5px\">");
		sb.append(getTimestampPrefix());
		sb.append(EncodeTools.forHtmlContent(msg));
		sb.append("<br />");
		sb.append("</div>");

		try {
			stream.writeText(sb.toString());
		} catch (NotesException e) {
			e.printStackTrace();
			System.out.println(event + " (" + user + "): " + msg);
		}

		errorCount++;
	}

	/**
	 * Logs exception stack trace
	 * 
	 * @param e
	 */
	public void logException(Throwable e)
	{
		if (isClosed())
			return;

		if (!isInitialized()) {
			try {
				initialize();
			} catch (NotesException ne) {
				ne.printStackTrace();
				System.out.println(event + " (" + user + "): " + ne.getMessage());
			}
		}

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String stackTrace = sw.toString();

		StringBuilder sb = new StringBuilder();
		sb.append(
				"<div style=\"font-family: Arial; font-size: 10px; font-weight: bold; color: #ff0000; margin-left: 5px; margin-right: 5px\">");
		sb.append(getTimestampPrefix());
		sb.append(EncodeTools.forHtmlContent(stackTrace));
		sb.append("<br />");
		sb.append("</div>");

		try {
			stream.writeText(sb.toString());
		} catch (NotesException ne) {
			ne.printStackTrace();
			System.out.println(event + " (" + user + "): " + ne.getMessage());
		}
		try {
			sw.close();
		} catch (IOException ex) {
		}
		pw.close();
		errorCount++;
	}

	/**
	 * Get timestamp as String
	 * 
	 * @return date as string formatted using user locale
	 */
	private String getTimestamp()
	{
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, new Locale("de"));
		return df.format(new Date());
	}

	/**
	 * Get timestamp prefix for log values
	 * 
	 * @return
	 */
	private String getTimestampPrefix()
	{
		return getTimestamp() + ": ";
	}

	public void recycle() throws NotesException
	{
		close();
	}

	@SuppressWarnings("rawtypes")
	public void recycle(Vector arg0) throws NotesException
	{
		close();
	}
}
