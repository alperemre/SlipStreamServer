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

import org.restlet.Context;
import org.restlet.routing.Router;
import org.restlet.routing.TemplateRoute;
import org.restlet.routing.Variable;

import com.sixsq.slipstream.exceptions.ConfigurationException;

public class DashboardRouter extends Router {

	public DashboardRouter(Context context) throws ConfigurationException {
		super(context);

		TemplateRoute route;
		route = attach("?user={user}", DashboardResource.class);
		route.setMatchingQuery(true);
		route.getTemplate().getVariables()
				.put("user", new Variable(Variable.TYPE_URI_QUERY));

		attach("", DashboardResource.class);

		attach("/", DashboardResource.class);
	}

}
