package com.sixsq.slipstream.util;

import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.persistence.EntityManager;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import com.sixsq.slipstream.connector.Connector;
import com.sixsq.slipstream.connector.ConnectorFactory;
import com.sixsq.slipstream.exceptions.CannotAdvanceFromTerminalStateException;
import com.sixsq.slipstream.exceptions.ConfigurationException;
import com.sixsq.slipstream.exceptions.InvalidStateException;
import com.sixsq.slipstream.exceptions.SlipStreamException;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.factory.RunFactory;
import com.sixsq.slipstream.persistence.PersistenceUtil;
import com.sixsq.slipstream.persistence.Run;
import com.sixsq.slipstream.persistence.User;
import com.sixsq.slipstream.persistence.UserParameter;
import com.sixsq.slipstream.statemachine.StateMachine;
import com.sixsq.slipstream.statemachine.States;

public class Terminator {
	
	/* I WILL BE BACK */
	
	public static int purge() throws ConfigurationException, ValidationException {
		List<Run> old = Run.listOldTransient();
		for (Run r : old) {
			EntityManager em = PersistenceUtil.createEntityManager();
			try {
				r = Run.load(r.getResourceUri(), em);
				purgeRun(r);
			} catch (SlipStreamException e) {
				Logger.getLogger("garbage-collector").log(Level.SEVERE, e.getMessage(), e.getCause());
			} finally {
				em.close();
			}
		}
		return old.size();
	}
	
	public static void purgeRun(Run run) throws SlipStreamException {
		Run.abort("The run has timed out", run.getUuid());
		
		boolean isGarbageCollected = Run.isGarbageCollected(run);
		Run.setGarbageCollected(run);
		run = run.store();
		
		String onErrorKeepRunning = run.getParameterValue(
				UserParameter.KEY_ON_ERROR_RUN_FOREVER, "false");
		
		if (! Boolean.parseBoolean(onErrorKeepRunning) && 
				(run.getState() == States.Initializing || isGarbageCollected)) {
			terminate(run.getResourceUri());
		}
		
		/* else {
			StateMachine sm = StateMachine.getStateMachine(run);
			sm.tryAdvanceState(true);
		}*/
	}

	public static void terminate(String runResourceUri) throws ValidationException, 
			CannotAdvanceFromTerminalStateException, InvalidStateException {

		EntityManager em = PersistenceUtil.createEntityManager();

		Run run = Run.load(runResourceUri, em);
		User user = User.loadByName(run.getUser());
		
		StateMachine sc = StateMachine.createStateMachine(run);
		
		if (sc.canCancel()) {
			terminateInstances(run, user);
			sc.tryAdvanceToCancelled();
		} else {
			if (sc.getState() == States.Ready) {
				sc.tryAdvanceToFinalizing();
			}
			terminateInstances(run, user);
			sc.tryAdvanceState(true);
		}
		
		em.close();
	}
	
	public static void terminateInstances(Run run, User user) throws ValidationException {
		HashSet<String> cloudServicesList = RunFactory.getCloudServicesList(run);
		for (String cloudServiceName : cloudServicesList) {
			Connector connector = ConnectorFactory.getConnector(cloudServiceName);
			try {
				connector.terminate(run, user);
			} catch (SlipStreamException e) {
				throw new ResourceException(
						Status.CLIENT_ERROR_CONFLICT, "Failed terminating VMs", e);
			}
		}
	}
	
}
