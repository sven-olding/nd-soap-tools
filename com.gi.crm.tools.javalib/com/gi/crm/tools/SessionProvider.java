package com.gi.crm.tools;

import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.WebServiceBase;

/**
 * Liefert eine aktuelle Notes Session
 * 
 * @author SOL
 */
public final class SessionProvider
{
	private static Session session = null;

	private SessionProvider()
	{
	}

	public static Session getSession()
	{
		session = WebServiceBase.getCurrentSession();
		if (session == null) {
			try {
				session = NotesFactory.createSession();
			} catch (NotesException e) {
				e.printStackTrace();
			}
		}
		return session;
	}
}
