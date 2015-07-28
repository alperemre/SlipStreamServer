package com.sixsq.slipstream.connector;

import com.sixsq.slipstream.persistence.Vm;

import java.util.*;
import java.util.logging.Logger;


/**
 * 
 * Set of static methods to start and end the recording of usage for a VM.
 * Collaborates with clojure code, and is called by Collector.
 * 
 * 
 * +=================================================================+
 * SlipStream Server (WAR) ===== Copyright (C) 2013 SixSq Sarl (sixsq.com) =====
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * -=================================================================- *
 */
public class UsageRecorder {

	private static Logger logger = Logger.getLogger(UsageRecorder.class.getName());

	public static boolean isMuted = false;

	private static Set<String> recordedVmInstanceIds = new HashSet<String>();

	public static void muteForTests() {
		isMuted = true;
		logger.severe("You should NOT see this message in production: usage will *not* be recorded");
	}

	public static void insertStart(String instanceId, String user, String cloud, List<UsageMetric> metrics) {
		try {

			if(isMuted) {
				return;
			}

			logger.info("Inserting usage record START for " + metrics + ", " + describe(instanceId, user, cloud));

			UsageRecord usageRecord = new UsageRecord(getAcl(user), user, cloud,
					keyCloudVMInstanceID(cloud, instanceId), new Date(), null, metrics);
			UsageRecord.post(usageRecord);

			recordedVmInstanceIds.add(cloudInstanceId(cloud, instanceId));

			logger.info("DONE Insert usage record START for " + describe(instanceId, user, cloud));
		} catch (Exception e) {
			logger.severe("Unable to insert usage record START:" + e.getMessage());
		}
	}

	public static void insertEnd(String instanceId, String user, String cloud, List<UsageMetric> metrics) {
		try {

			if(isMuted) {
				return;
			}

			logger.info("Inserting usage record END, metrics" + metrics + ", for " + describe(instanceId, user, cloud));

			UsageRecord usageRecord = new UsageRecord(getAcl(user), user, cloud,
					keyCloudVMInstanceID(cloud, instanceId), null, new Date(), metrics);
			UsageRecord.post(usageRecord);

			recordedVmInstanceIds.remove(cloudInstanceId(cloud, instanceId));

			logger.info("DONE Insert usage record END for " + describe(instanceId, user, cloud));
		} catch (Exception e) {
			logger.severe("Unable to insert usage record END:" + e.getMessage());
		}
	}

	public static boolean hasRecorded(String cloud, String instanceId) {
		logger.info("UsageRecorder, recordedVmInstanceIds = " + recordedVmInstanceIds);
		return recordedVmInstanceIds.contains(cloudInstanceId(cloud, instanceId));
	}

	public static void insertRestart(String instanceId, String user, String cloud, List<UsageMetric> metrics) {
		insertEnd(instanceId, user, cloud, metrics);
		insertStart(instanceId, user, cloud, metrics);
	}

	private static ACL getAcl(String user) {
		TypePrincipal owner = new TypePrincipal(TypePrincipal.PrincipalType.USER, user);
		List<TypePrincipalRight> rules = Arrays.asList(
				new TypePrincipalRight(TypePrincipal.PrincipalType.USER, user, TypePrincipalRight.Right.ALL),
				new TypePrincipalRight(TypePrincipal.PrincipalType.ROLE, "ADMIN", TypePrincipalRight.Right.ALL));
		return new ACL(owner, rules);
	}

	public static List<UsageMetric> createVmMetrics(Vm vm) {
		List<UsageMetric> metrics = new ArrayList<UsageMetric>();

		metrics.add(new UsageMetric("vm", "1.0"));

		Integer cpu = vm.getCpu();
		if (cpu != null) {
			metrics.add(new UsageMetric(ConnectorBase.VM_CPU, cpu.toString()));
		}

		Float ram = vm.getRam();
		if (ram != null) {
			metrics.add(new UsageMetric(ConnectorBase.VM_RAM, ram.toString()));
		}

		Float disk = vm.getDisk();
		if (disk != null) {
			metrics.add(new UsageMetric(ConnectorBase.VM_DISK, disk.toString()));
		}

		String instanceType = vm.getInstanceType();
		if (instanceType != null && !instanceType.isEmpty()) {
			metrics.add(new UsageMetric("instance-type." + instanceType, "1.0"));
		}
				
		return metrics;
	}

	private static String keyCloudVMInstanceID(String cloud, String instanceId) {
		return cloud + ":" + instanceId;
	}

//	// TODO : factor out common functions with Event class
//	private static final String ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
//
//	private static final DateFormat ISO8601Formatter = new SimpleDateFormat(ISO_8601_PATTERN, Locale.US);
//
//	static {
//		ISO8601Formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
//	}

	private static String cloudInstanceId(String cloud, String instanceId) {
		return cloud + ":" + instanceId;
	}

	private static String describe(String instanceId, String user, String cloud) {
		return "[" + user + ":" + cloud + "/" + instanceId + "]";
	}
}
