package com.universeprojects.miniup.server.commands;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.Key;
import com.universeprojects.cacheddatastore.CachedDatastoreService;
import com.universeprojects.cacheddatastore.CachedEntity;
import com.universeprojects.miniup.server.GameUtils;
import com.universeprojects.miniup.server.ODPAuthenticator;
import com.universeprojects.miniup.server.ODPDBAccess;
import com.universeprojects.miniup.server.commands.framework.Command;
import com.universeprojects.miniup.server.commands.framework.UserErrorMessage;
import com.universeprojects.miniup.server.commands.framework.Command.JavascriptResponse;
import com.universeprojects.miniup.server.services.CombatService;
import com.universeprojects.miniup.server.services.MainPageUpdateService;

public class CommandCombatEscape extends Command {

	public CommandCombatEscape(ODPDBAccess db, HttpServletRequest request,
			HttpServletResponse response) {
		super(db, request, response);
	}

	@Override
	public void run(Map<String, String> parameters) throws UserErrorMessage {
		ODPDBAccess db = ODPDBAccess.getInstance(request);
		CachedDatastoreService ds = getDS();
		CachedEntity character = db.getCurrentCharacter();
		CachedEntity user = db.getCurrentUser();
		CombatService cs = new CombatService(db);
		
		CachedEntity monster = db.getCharacterCombatant(character);
		if (monster==null)
            throw new RuntimeException("Inconsistent database state. Character is in combat mode, but no combatant is set.");
		
		CachedEntity location = db.getEntity((Key)character.getProperty("locationKey"));
		if(location == null)
			throw new RuntimeException("Character cannot be in combat in a null location");
		
		// Throws UserErrorMessage. This is a valid situation, as it means the character is attempting 
		// to do something not allowed (running as non-party leader, escaping while defending, etc).
		boolean success = db.doCharacterAttemptEscape(location, character, monster);
		db.flagNotALooter(request);
		
		String userMessage = "";
		if(success)
		{
			userMessage = "You managed to escape!";
		}
		else
		{
			ODPAuthenticator auth = new ODPAuthenticator();
			String counterAttackStatus = db.doMonsterCounterAttack(auth, user, monster, character);
			
			if (((Double)monster.getProperty("hitpoints"))>0)
            {
                userMessage+="<br><br>";
                userMessage+="<h3>The "+monster.getProperty("name")+" attacks you as you're fleeing...</h3>";

                if (counterAttackStatus==null)
                {
                    userMessage+="The "+monster.getProperty("name")+" missed!";
                }
                else 
                {
                    userMessage+=counterAttackStatus;
                }
            }
		}
		
		MainPageUpdateService mpus = new MainPageUpdateService(db, user, character, location, this);
		String combatUpdate = mpus.updateCombatView(cs, monster, userMessage);
		if(combatUpdate == null || combatUpdate.isEmpty())
		{
			transitionFromCombat(userMessage);
		}
	}

	private void transitionFromCombat(String updateMessage)
	{
		// TODO: Convert to refreshless transition from combat to non-combat.
		// Refresh full page for now.
		if(updateMessage != null && updateMessage.isEmpty() == false)
			GameUtils.addMessageForClient(request, updateMessage);
		setJavascriptResponse(JavascriptResponse.FullPageRefresh);
	}
}
