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

import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import com.sixsq.slipstream.exceptions.ConfigurationException;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.persistence.User;
import com.sixsq.slipstream.run.RunView;


@Root(name = "runs")
public class RunViewList {

	@Attribute(required=false)
	private int offset;

	@Attribute(required=false)
	private int limit;

	@Attribute(required=false)
	private int count;

	@ElementList(inline = true, required = false)
	private List<RunView> runs;

	public RunViewList() {
	}

	public List<RunView> getRuns() {
		return runs;
	}

	public void populate(User user, int offset, int limit) throws ConfigurationException, ValidationException {
		populate(user, null, offset, limit);
	}

	public void populate(User user, String moduleResourceUri, int offset, int limit)
			throws ConfigurationException, ValidationException {
		user.validate();
		populateRuns(user, moduleResourceUri, offset, limit);
	}

	private void populateRuns(User user, String moduleResourceUri, int offset, int limit)
			throws ConfigurationException, ValidationException {
		this.offset = offset;
		this.limit = limit;

		count = RunView.fetchListViewCount(user, moduleResourceUri);
		runs = RunView.fetchListView(user, moduleResourceUri, offset, limit);
	}

}
