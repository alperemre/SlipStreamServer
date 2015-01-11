package com.sixsq.slipstream.connector;

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
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sixsq.slipstream.configuration.Configuration;
import com.sixsq.slipstream.exceptions.ConfigurationException;
import com.sixsq.slipstream.exceptions.SlipStreamException;
import com.sixsq.slipstream.exceptions.SlipStreamRuntimeException;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.metrics.Metrics;
import com.sixsq.slipstream.metrics.MetricsTimer;
import com.sixsq.slipstream.persistence.User;
import com.sixsq.slipstream.persistence.Vm;

public class Collector {

	private static Logger logger = Logger.getLogger(Collector.class.getName());
	private static MetricsTimer collectTimer = Metrics.newTimer(Collector.class, "collect");

	public static int collect(User user, Connector connector) {
		int res = 0;
		collectTimer.start();
		try {
			if (connector.isCredentialsSet(user)) {
				res = describeInstances(user, connector);
			}
		} catch (ConfigurationException e) {
			logger.severe(e.getMessage());
		} catch (ValidationException e) {
			logger.warning(e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.warning(e.getMessage());
		} finally {
			collectTimer.stop();
		}
		return res;
	}

	private static int describeInstances(User user, Connector connector)
			throws ConfigurationException, ValidationException {
		user.addSystemParametersIntoUser(Configuration.getInstance()
				.getParameters());
		Properties props = new Properties();
		try {
			props = connector.describeInstances(user);
		} catch (SlipStreamException e) {
			logger.warning("Failed contacting cloud: "
					+ connector.getConnectorInstanceName() + " on behalf of "
					+ user.getName() + " with '" + e.getMessage() + "'");
			return 0;
		} catch (SlipStreamRuntimeException e) {
			logger.warning("Failed contacting cloud: "
					+ connector.getConnectorInstanceName() + " on behalf of "
					+ user.getName() + " with '" + e.getMessage() + "'");
		} catch (Exception e) {
			logger.log(
					Level.SEVERE,
					"Error in describeInstances "
							+ "(cloud: " + connector.getConnectorInstanceName()
							+ ", user: " + user.getName() + "): " + e.getMessage(), e);
		}

		return populateVmsForCloud(user, connector.getConnectorInstanceName(), props);
	}

	private static int populateVmsForCloud(User user, String cloud, Properties idsAndStates) {
		List<Vm> vms = new ArrayList<Vm>();
		for (String instanceId : idsAndStates.stringPropertyNames()) {
			String state = (String) idsAndStates.get(instanceId);
			Vm vm = new Vm(instanceId, cloud, state, user.getName());
			vms.add(vm);
		}
		Vm.update(vms, user.getName(), cloud);
		return idsAndStates.size();
	}
}
