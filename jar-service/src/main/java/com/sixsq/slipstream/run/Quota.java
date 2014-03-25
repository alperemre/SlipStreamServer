package com.sixsq.slipstream.run;

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

import java.util.Map;

import com.sixsq.slipstream.configuration.Configuration;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.exceptions.QuotaException;
import com.sixsq.slipstream.persistence.Parameter;
import com.sixsq.slipstream.persistence.QuotaParameter;
import com.sixsq.slipstream.persistence.RuntimeParameter;
import com.sixsq.slipstream.persistence.User;

/**
 * Unit test:
 * 
 * @see QuotaTest
 * 
 */

public class Quota {

        public static void validate(User user, Map<String, Integer> request, Map<String, Integer> usage)
			throws ValidationException, QuotaException {
		for (Map.Entry<String, Integer> entry : request.entrySet()) {
			String cloud = entry.getKey();
			int nodesRequested = entry.getValue();
			
                        String quota = getValue(user, cloud);

			Integer currentUsage = usage.get(cloud);
			if (currentUsage == null) currentUsage = 0;

			if ((currentUsage + nodesRequested) > Integer.parseInt(quota)) {
				throw new QuotaException(
						"Cannot run because your quota will be exceeded");
			}
		}

	}

	public static String getValue(User user, String cloud) throws ValidationException {
		Parameter parameter = user.getParameter(
			cloud + 
                        RuntimeParameter.PARAM_WORD_SEPARATOR + 
                        QuotaParameter.QUOTA_VM_PARAMETER_NAME, cloud);
		if (parameter != null) {
			return parameter.getValue();
		} else {
			return Configuration.getInstance().getParameters().getParameterValue(
				cloud + RuntimeParameter.PARAM_WORD_SEPARATOR + QuotaParameter.QUOTA_VM_PARAMETER_NAME,
				QuotaParameter.QUOTA_VM_DEFAULT);
		}
	}

}
