package com.sixsq.slipstream.resource;

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

import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;

import com.sixsq.slipstream.module.ModuleListResource;
import com.sixsq.slipstream.module.ModuleView.ModuleViewList;
import com.sixsq.slipstream.util.HtmlUtil;

public abstract class SimpleResource extends ModuleListResource {

	@Get("html")
	public Representation toHtml() {

		ModuleViewList moduleViewList = retrieveFilteredModuleViewList();

		return new StringRepresentation(
				HtmlUtil.toHtml(moduleViewList,
						getPageRepresentation(), getTransformationType(), getUser()),
				MediaType.TEXT_HTML);
	}

	protected abstract String getPageRepresentation();

}
