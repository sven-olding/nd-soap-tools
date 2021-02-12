import com.gi.crm.log.SimpleLog;
import com.gi.crm.soap.WebServiceTransfer;
import com.gi.crm.tools.Tools;

import lotus.domino.AgentBase;
import lotus.domino.AgentContext;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class JavaAgent extends AgentBase
{
	private Session			session					= null;
	private AgentContext	agentContext			= null;
	private Document		contextDoc				= null;
	private SimpleLog		log						= null;
	private Document		transferDoc				= null;
	private Database		contextDb				= null;
	private View			contextIdStructureView	= null;
	private String			serviceName				= null;

	@Override
	public void NotesMain()
	{
		String errMsg = null;

		try {
			session = getSession();
			agentContext = session.getAgentContext();
			serviceName = transferDoc.getItemValueString("ServiceAlias");

			String paraNoteId = agentContext.getCurrentAgent().getParameterDocID();
			if (paraNoteId != null && !paraNoteId.isEmpty()) {
				transferDoc = agentContext.getCurrentDatabase().getDocumentByID(paraNoteId);
			} else {
				transferDoc = agentContext.getDocumentContext();
			}
			transferDoc.replaceItemValue("agentValueReturnCode", "0");

			String fdMe = transferDoc.getItemValueString("CTX_fdMe");
			String[] fdMeSplit = fdMe.split("\\|");
			String dbName = fdMeSplit[2];

			initLog();

			log.log("MessageID: " + transferDoc.getItemValueString("MessageID"));
			log.log("fdMe: " + fdMe);

			String path[] = Tools.getDatabasePath(dbName);

			contextDb = session.getDatabase(path[0], path[1], false);
			contextIdStructureView = contextDb.getView("vaCEIDStructure");
			contextIdStructureView.refresh();

			contextDoc = contextIdStructureView.getDocumentByKey(fdMe.split("\\|")[0], true);
			if (contextDoc == null) {
				errMsg = "CRM Dokument nicht gefunden!";
				log.log(errMsg);
				transferDoc.replaceItemValue("Error", errMsg);
				transferDoc.replaceItemValue("Status", "Error");
			} else {

				WebServiceTransfer transfer = new WebServiceTransfer(serviceName, log);

				String responseString = transfer.sendMessage(transferDoc, contextDoc);

				log.log("Response:");
				log.log(responseString);

				if (responseString.isEmpty()) {

					transferDoc.replaceItemValue("agentValueReturnCode", "1");

				} else {
					errMsg = transfer.getErrorMessageFromResponse(responseString);
					if (!errMsg.isEmpty()) {
						log.logError(errMsg);
						transferDoc.replaceItemValue("Error", errMsg);
						transferDoc.replaceItemValue("Status", "Error");
					} else {
						transfer.outputMapping(responseString, transferDoc, contextDoc);

						transferDoc.replaceItemValue("Error", "");
						transferDoc.replaceItemValue("Status", "Completed");

						transferDoc.replaceItemValue("agentValueReturnCode", "1");

						Tools.createAdminRequestDataSync(contextDoc);
					}
				}
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
			if (contextDoc != null) {
				try {
					contextDoc.save();
					contextDoc.recycle();
				} catch (NotesException e) {
				}
			}
			if (contextIdStructureView != null) {
				try {
					contextIdStructureView.recycle();
				} catch (NotesException e) {
				}
			}

			if (contextDb != null) {
				try {
					contextDb.recycle();
				} catch (NotesException e) {
				}
			}

			closeLog();
		}
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
		log = new SimpleLog(session.getCurrentDatabase(), serviceName, session.getCurrentDatabase().getTitle(),
				session.getEffectiveUserName());

		log.log("START Transfer");
		log.addNewLine();
	}

}