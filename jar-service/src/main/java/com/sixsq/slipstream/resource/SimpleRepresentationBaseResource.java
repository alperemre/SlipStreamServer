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
import org.restlet.data.Status;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ResourceException;

import com.sixsq.slipstream.util.HtmlUtil;

public abstract class SimpleRepresentationBaseResource extends BaseResource {

	private String message;

	abstract protected String getPageRepresentation();


	protected void setResponse(String content, MediaType mediaType, Status status) {
		getResponse().setEntity(new StringRepresentation(content, mediaType));
		getResponse().setStatus(status);
	}

	protected void setPostResponse(String content, MediaType mediaType) {
		getResponse().setEntity(new StringRepresentation(content, mediaType));
		getResponse().setStatus(Status.SUCCESS_CREATED);
	}

	protected void setPostResponse() {
		getResponse().setEntity(generateHtml(), MediaType.TEXT_HTML);
		getResponse().setStatus(Status.SUCCESS_CREATED);
	}

	protected void handleError(Exception e) {
		e.printStackTrace();
		throw(new ResourceException(Status.SERVER_ERROR_INTERNAL, e));
	}

	protected String generateHtml() {
		
		return HtmlUtil.toHtml(getMessage(),
				getPageRepresentation(), getUser(), getRequest());

	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
