package com.sixsq.slipstream.metering;

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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.sixsq.slipstream.exceptions.ConfigurationException;
import com.sixsq.slipstream.exceptions.SlipStreamException;
import com.sixsq.slipstream.metering.Metering;
import com.sixsq.slipstream.persistence.Vm;
import com.sixsq.slipstream.run.RunTestBase;
import com.sixsq.slipstream.util.CommonTestUtil;


@Ignore
public class MeteringTest extends RunTestBase {

	private static String CLOUD_A = "local";
	private static String CLOUD_B = "cloudB";
	private static String CLOUD_C = "cloudC";
	private static String RUNNING_VM_STATE = "Running";


	@BeforeClass
	public static void setupClass() throws ConfigurationException, SlipStreamException {
		createUser();
		String username = user.getName();

		//CommonTestUtil.addSshKeys(user);

		CommonTestUtil.setCloudConnector(CLOUD_A + ":local," +
										 CLOUD_B + ":cloudstack," +
										 CLOUD_C + ":openstack");
/*
		setupDeployments();
		Run run = createAndStoreRun(deployment, username);
		String runId = run.getUuid();
 */
		String runId = "xxx";

		List<Vm> vms = new ArrayList<Vm>();

		vms.add(createVm("id_1", CLOUD_A, RUNNING_VM_STATE, username, runId));
		vms.add(createVm("id_2", CLOUD_A, RUNNING_VM_STATE, username, runId));
		vms.add(createVm("id_3", CLOUD_A, "Terminated", username, runId));
		Vm.update(vms, username, CLOUD_A);

		vms.add(createVm("id_1", CLOUD_B, "Pending", username, runId));
		vms.add(createVm("id_2", CLOUD_B, RUNNING_VM_STATE, username, runId));
		vms.add(createVm("id_3", CLOUD_B, "Terminated", username, runId));
		Vm.update(vms, username, CLOUD_B);

	}

	private static Vm createVm(String instanceid, String cloud, String state, String user, String runId) {
		Vm vm = new Vm(instanceid, cloud, state, user);
		vm.setRunUuid(runId);
		return vm;
	}

	@Test
	public void computeImageBuildRun() throws SlipStreamException {

		String data = Metering.populate(user);

		String cloudAdata = getCloudData(CLOUD_A, "2");
		String cloudBdata = getCloudData(CLOUD_B, "1");
		String cloudCdata = getCloudData(CLOUD_C, "0");

		assertEquals(true, data.contains(cloudAdata));
		assertEquals(true, data.contains(cloudBdata));
		assertEquals(true, data.contains(cloudCdata));
		assertEquals(true, data.endsWith("\n"));

	}

	private String getCloudData(String cloudServiceName, String numberOfInstances) {
		return "slipstream." + user.getName() + ".usage.instance." + cloudServiceName + " " + numberOfInstances;
	}

}
