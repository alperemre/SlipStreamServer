package com.sixsq.slipstream.application;

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

import static org.restlet.data.MediaType.APPLICATION_XHTML;
import static org.restlet.data.MediaType.APPLICATION_XML;
import static org.restlet.data.MediaType.TEXT_HTML;
import static org.restlet.data.MediaType.TEXT_PLAIN;

import java.util.List;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ClientInfo;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.service.StatusService;
import org.w3c.dom.Document;

import com.sixsq.slipstream.configuration.Configuration;
import com.sixsq.slipstream.exceptions.ConfigurationException;
import com.sixsq.slipstream.persistence.ServiceConfiguration;
import com.sixsq.slipstream.persistence.ServiceConfigurationParameter;
import com.sixsq.slipstream.persistence.User;
import com.sixsq.slipstream.util.HtmlUtil;
import com.sixsq.slipstream.util.RequestUtil;
import com.sixsq.slipstream.util.SerializationUtil;

public class CommonStatusService extends StatusService {

	@Override
	public Representation getRepresentation(Status status, Request request,
			Response response) {

		try {
			reloadParameters();
		} catch (ConfigurationException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}

		Representation representation = null;

		User user = RequestUtil.getUserFromRequest(request);

		String baseUrlSlash = RequestUtil.getBaseUrlSlash(request);

		Configuration configuration = RequestUtil
				.getConfigurationFromRequest(request);

		ClientInfo clientInfo = request.getClientInfo();
		List<Preference<MediaType>> mediaTypes = clientInfo
				.getAcceptedMediaTypes();

		String error = statusToString(status);

		for (Preference<MediaType> preference : mediaTypes) {

			MediaType desiredMediaType = preference.getMetadata();

			if (TEXT_HTML.isCompatible(desiredMediaType)) {

				return toXhtml(status, response, user, baseUrlSlash,
						configuration.version);

			} else if (APPLICATION_XHTML.isCompatible(desiredMediaType)) {

				return toXhtml(status, response, user, baseUrlSlash,
						configuration.version);

			} else if (TEXT_PLAIN.isCompatible(desiredMediaType)) {

				representation = new StringRepresentation(error);
				representation.setMediaType(TEXT_PLAIN);
				return representation;

			} else if (APPLICATION_XML.isCompatible(desiredMediaType)) {

				representation = new StringRepresentation("<error code=\""
						+ status.getCode() + "\">" + error + "</error>");
				representation.setMediaType(APPLICATION_XML);

				return representation;
			}
		}

		return representation;
	}

	private void reloadParameters() throws ConfigurationException {
		Configuration configuration = Configuration.getInstance();

		String key = ServiceConfiguration.RequiredParameters.SLIPSTREAM_SUPPORT_EMAIL
				.getName();
		ServiceConfigurationParameter parameter = configuration.getParameters()
				.getParameter(key);
		String email = parameter.getValue();

		setContactEmail(email);
	}

	private Representation toXhtml(Status status, Response response, User user,
			String baseUrlSlash, String version) {

		String metadata = "";

		if (user != null) {
			Document doc = SerializationUtil.toXmlDocument(user);
			metadata = SerializationUtil.documentToString(doc);
		}

		return new StringRepresentation(HtmlUtil.toHtmlError(metadata,
				status.getDescription(), status.getCode()), MediaType.TEXT_HTML);
	}

	private String statusToString(Status status) {
		return "Error: " + status.getDescription() + " (" + status.getCode()
				+ " - " + status.getName() + ")";
	}
}
