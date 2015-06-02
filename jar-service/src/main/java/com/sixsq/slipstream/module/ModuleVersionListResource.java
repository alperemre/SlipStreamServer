package com.sixsq.slipstream.module;

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

import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

import com.sixsq.slipstream.module.ModuleVersionView.ModuleVersionViewList;
import com.sixsq.slipstream.persistence.Module;
import com.sixsq.slipstream.resource.BaseResource;
import com.sixsq.slipstream.util.HtmlUtil;
import com.sixsq.slipstream.util.ResourceUriUtil;
import com.sixsq.slipstream.util.SerializationUtil;

public class ModuleVersionListResource extends BaseResource {

	private String id = null;

	@Override
	public void initialize() throws ResourceException {

		id = ResourceUriUtil.extractResourceUri(getRequest());

		if (id == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST);
		}
	}

	@Override
	protected void authorize() {

		Module m = Module.loadLatest(id);

		if (getUser().isSuper()) {
			return;
		}

		if (m.getAuthz().canGet(getUser())) {
			return;

		}

		throwClientForbiddenError("Not allowed to access: " + id);
	}

	@Get("txt")
	public Representation toTxt() {

		String viewList = toXml(Module.viewListAllVersions(id));
		return new StringRepresentation(viewList);
	}

	@Get("xml")
	public Representation toXml() {

		ModuleVersionViewList list = new ModuleVersionViewList(
				Module.viewListAllVersions(id));
		String result = SerializationUtil.toXmlString(list);
		return new StringRepresentation(result, MediaType.APPLICATION_XML);
	}

	@Get("json")
	public Representation toJson() {

		List<ModuleVersionView> list = Module.viewListAllVersions(id);
		String result = SerializationUtil.toJsonString(list);
		return new StringRepresentation(result, MediaType.APPLICATION_JSON);
	}

	@Get("html")
	public Representation toHtml() {

		ModuleVersionViewList list = new ModuleVersionViewList(
				Module.viewListAllVersions(id));

		return new StringRepresentation(
				HtmlUtil.toHtml(list,
						getPageRepresentation(), getUser(), getRequest()),
				MediaType.TEXT_HTML);
	}

	private String toXml(List<ModuleVersionView> viewList) {
		ModuleVersionViewList moduleViewList = new ModuleVersionViewList(
				viewList);
		return SerializationUtil.toXmlString(moduleViewList);
	}

	protected String getPageRepresentation() {
		return "versions";
	}

}
