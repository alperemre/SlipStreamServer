package com.sixsq.slipstream.dashboard;

/*
 * +=================================================================+
 * SlipStream Server (WAR)
 * =====
 * Copyright (C) 2013 SixSq Sarl (sixsq.com)
 * =====
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -=================================================================-
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import com.sixsq.slipstream.connector.Connector;
import com.sixsq.slipstream.connector.ConnectorFactory;
import com.sixsq.slipstream.exceptions.SlipStreamClientException;
import com.sixsq.slipstream.exceptions.SlipStreamException;
import com.sixsq.slipstream.persistence.Run;
import com.sixsq.slipstream.persistence.User;
import com.sixsq.slipstream.run.RunView;
import com.sixsq.slipstream.run.RunView.RunViewList;

@Root
public class Dashboard {

	@Root(name = "vms")
	public static class VmViewList {

		@ElementList(inline = true, required = false)
		private List<VmView> list = new ArrayList<VmView>();

		public VmViewList() {
		}

		public VmViewList(List<VmView> list) {
			this.list = list;
		}

		public List<VmView> getList() {
			return list;
		}

	}

	@Element
	private RunViewList runs;

	@Element
	private VmViewList vms = new VmViewList();

	public RunViewList getRuns() {
		return runs;
	}

	public VmViewList getVms() {
		return vms;
	}

	public void populate(User user) throws SlipStreamException {
		user.validate();
		User.validateMinimumInfo(user);
		populateRuns(user, user.isSuper());
		populateVms(user);
	}

	private void populateRuns(User user, boolean isSuper) {
		runs = RunView.fetchListView(user, isSuper);
	}

	private void populateVms(User user) throws SlipStreamException {
		Properties describeInstancesStates = new Properties();

		for (String cloudServiceName : ConnectorFactory.getCloudServiceNames()) {
			Connector connector = ConnectorFactory.getConnector(cloudServiceName);
			Properties props = new Properties();
			try {
				props = connector.describeInstances(user);
			} catch (SlipStreamClientException e) {
				// swallow the exception, since we don't want to fail if users have
				// wrong credentials
			}
			for (String key : props.stringPropertyNames()) {
				describeInstancesStates.put(key, props.getProperty(key));
			}
			
		}
		
		for (Entry<Object, Object> entry : describeInstancesStates.entrySet()) {
			String instanceId = (String) entry.getKey();
			String status = (String) entry.getValue();
			String runUuid = fetchRunUuid(user, instanceId);
			vms.getList().add(new VmView(instanceId, status, runUuid));
		}
	}

	protected String fetchRunUuid(User user, String instanceId) {
		List<RunView> runs = Run.viewListByInstanceId(user, instanceId);
		if (runs.size() == 0) {
			return "Unknown";
		}
		return runs.get(0).uuid;
	}

}
