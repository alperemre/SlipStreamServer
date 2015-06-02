package com.sixsq.slipstream.user;

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

import java.util.Date;
import java.util.List;

import flexjson.JSON;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import com.sixsq.slipstream.persistence.User;

@Root(name = "item")
public class UserView {

	@Attribute
	public final String name;

	@Attribute
	public final String id;

	@Attribute(required = false)
	public final String firstName;

	@Attribute(required = false)
	public final String lastName;

	@Attribute(required = false)
	public final String email;

	@Attribute(required = false)
	public final User.State state;

	@Attribute(required = false)
	public final Date activeSince;

	@Attribute(required = false)
	public final Date lastOnline;

	@Attribute(required = false)
	public final Date lastExecute;

	@Attribute(required = false)
	public String organization;

	@Attribute(required = false, name = "issuper")
	@JSON(name = "issuper")
	public boolean isSuperUser;

	public UserView(String name, String firstName, String lastName, String email, User.State state, Date lastOnline,
			Date lastExecute, Date activeSince, String organization, Boolean isSuperUser) {

		this.name = name;
		this.id = User.constructResourceUri(name);

		this.firstName = firstName;
		this.lastName = lastName;

		this.email = email;

		this.state = state;

		if (lastOnline != null) {
			this.lastOnline = (Date) lastOnline.clone();
		} else {
			this.lastOnline = null;
		}

		if (lastExecute != null) {
			this.lastExecute = (Date) lastExecute.clone();
		} else {
			this.lastExecute = null;
		}

		this.activeSince = activeSince;

        this.organization = organization;
        this.isSuperUser = isSuperUser;
	}

	@Root(name = "list")
	public static class UserViewList {

		@ElementList(inline = true, required = false)
		private final List<UserView> list;

		public UserViewList(List<UserView> list) {
			this.list = list;
		}
	}

	@Attribute(required = false)
	public void setOnline(boolean online) {
	}

	@Attribute(required = false)
	public boolean isOnline() {
		return User.isOnline(lastOnline);
	}

}
