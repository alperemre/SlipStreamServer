package com.sixsq.slipstream.factory;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sixsq.slipstream.connector.CloudService;
import com.sixsq.slipstream.exceptions.ConfigurationException;
import com.sixsq.slipstream.exceptions.InvalidMetadataException;
import com.sixsq.slipstream.exceptions.NotFoundException;
import com.sixsq.slipstream.exceptions.SlipStreamClientException;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.persistence.*;

public class BuildImageFactory extends RunFactory {

	protected final static String nodeInstanceName = Run.MACHINE_NAME;

	@Override
	protected RunType getRunType() {
		return RunType.Machine;
	}

	@Override
	protected void validateModule(Module module, Map<String, ConnectorInstance> cloudServicePerNode)
			throws SlipStreamClientException {

		ImageModule image = castToRequiredModuleType(module);
		if (image.isBase()) {
			throw new SlipStreamClientException("A base image cannot be built");
		}

		checkNoCircularDependencies(image);

		checkHasSomethingToBuild(image);

		ConnectorInstance cloudService = cloudServicePerNode.get(nodeInstanceName);
		checkNotAlreadyBuilt(image, cloudService.getName());

		// Finding an image id will validate that one exists
		image.extractBaseImageId(cloudService.getName());
	}

	protected static void checkNoCircularDependencies(ImageModule image)
			throws InvalidMetadataException, ValidationException {
		List<String> visitedReferenceModules = new ArrayList<String>();
		recurseDependencies(image, visitedReferenceModules);
	}

	private static void recurseDependencies(ImageModule image,
			List<String> visitedReferenceModules)
			throws InvalidMetadataException, ValidationException {
		for (String ref : visitedReferenceModules) {
			if (ref.equals(image.getId())) {
				throw new InvalidMetadataException(
						"Circular dependency detected in module "
								+ image.getId());
			}
		}
		visitedReferenceModules.add(image.getId());
		if (image.getModuleReferenceUri() != null) {
			recurseDependencies(
					(ImageModule) ImageModule.load(image.getModuleReferenceUri()),
					visitedReferenceModules);
		}
		return;
	}

	private static void checkHasSomethingToBuild(ImageModule image)
			throws ValidationException {
		// Check if the image declares a list of packages or a recipe.
		// If it doesn't, we don't need to build the image. However, we
		// need to make sure that the referenced image is built.
		if (image.isVirtual()) {
			throw new ValidationException(
					"This image doesn't need to be built since it doesn't contain a package list nor a pre-recipe or a recipe.");
		}
	}

	private static void checkNotAlreadyBuilt(ImageModule image,
			String cloudServiceName) throws ValidationException {
		// Check that the image is not already built for this cloud service name
		if (image.getCloudImageIdentifier(cloudServiceName) != null) {
			throw new ValidationException(
					"This image was already built for cloud: "
							+ cloudServiceName);
		}
	}

//	@Override
//	protected void init(Module module, Run run, User user)
//			throws ValidationException, NotFoundException {
//
//		initRuntimeParameters((ImageModule) module, run);
//		initMachineState(run);
//
//		String cloudService = run.getCloudServiceNameForNode(nodeInstanceName);
//		initNodeNames(run, cloudService);
//	}

	protected static void initMachineState(Run run) throws ValidationException,
			NotFoundException {

		assignCommonNodeInstanceRuntimeParameters(run, nodeInstanceName);
	}

	protected static void initRuntimeParameters(ImageModule image, Run run)
			throws ValidationException, NotFoundException {

		// Add default values for the params as set in the image
		// definition
		// Only process the standard categories and the cloud service
		// (not the other cloud services, if any)

		List<String> filter = new ArrayList<String>();
		for (ParameterCategory c : ParameterCategory.values()) {
			filter.add(c.toString());
		}
		String cloudServiceName = run.getCloudServiceNameForNode(nodeInstanceName);
		filter.add(cloudServiceName);

		if (image.getParameters() != null) {
			for (Parameter param : image.getParameterList()) {
				if (filter.contains(param.getCategory())) {
					run.assignRuntimeParameter(constructParamName(nodeInstanceName, param.getName()),
							extractInitialValue(param, run),
							param.getDescription());
				}
			}
		}

		// Add cloud service name to orchestrator and machine
		run.assignRuntimeParameter(constructParamName(nodeInstanceName, RuntimeParameter.CLOUD_SERVICE_NAME),
				cloudServiceName, RuntimeParameter.CLOUD_SERVICE_DESCRIPTION);

		String imageId = image.extractBaseImageId(cloudServiceName);
		run.assignRuntimeParameter(constructParamName(nodeInstanceName, RuntimeParameter.IMAGE_ID_PARAMETER_NAME),
				imageId, RuntimeParameter.IMAGE_ID_PARAMETER_DESCRIPTION, ParameterType.String);

		String imagePlatform = image.getPlatform();
		run.assignRuntimeParameter(constructParamName(nodeInstanceName, RuntimeParameter.IMAGE_PLATFORM_PARAMETER_NAME),
				imagePlatform, RuntimeParameter.IMAGE_PLATFORM_PARAMETER_DESCRIPTION, ParameterType.String);

	}

	private static String extractInitialValue(Parameter parameter, Run run) {
		String parameterName = parameter.getName();

		String value = run.getParameterValue(constructParamName(nodeInstanceName, parameterName), null);
		if (value == null) {
			value = parameter.getValue();
		}

		return value;
	}

	protected void initNodes(Run run, String cloudService)
			throws ConfigurationException, ValidationException {
		run.addNodeInstance(nodeInstanceName, cloudService);
		// For build image, nodeInstanceName is the same as node (i.e. machine)
		run.addNode(nodeInstanceName, cloudService);
	}

	@Override
	protected void addUserFormParametersAsRunParameters(Module module, Run run,
			Map<String, List<Parameter>> userChoices) throws ValidationException {

		if (!isProvidedUserChoicesForNodeInstance(userChoices, nodeInstanceName)) {
				return;
		}

		ImageModule image = (ImageModule) module;
		List<Parameter> userChoicesForMachine = userChoices.get(nodeInstanceName);

		for (Parameter parameter : userChoicesForMachine) {
			checkParameterIsValid(image, parameter);

			String key = constructParamName(nodeInstanceName, parameter.getName());
			Parameter rp = new Parameter(key, parameter.getValue(), "");
			run.setParameter(rp);
		}
	}

	// TODO: pull this method up to be used in other factories.
	public static boolean isProvidedUserChoicesForNodeInstance(Map<String, List<Parameter>> userChoices,
	        String nodeInstanceName) {
		if (userChoices == null || userChoices.isEmpty())
			return false;

		List<Parameter> paramsForNodeInstance = userChoices.get(nodeInstanceName);
		if (paramsForNodeInstance == null || paramsForNodeInstance.isEmpty())
			return false;

		return true;
	}

	private void checkParameterIsValid(ImageModule image, Parameter parameter) throws ValidationException {
		List<String> paramsToFilter = new ArrayList<String>();
		paramsToFilter.add(RuntimeParameter.MULTIPLICITY_PARAMETER_NAME);
		paramsToFilter.add(RuntimeParameter.CLOUD_SERVICE_NAME);

		String paramName = parameter.getName();
		if (!image.getParameters().containsKey(paramName) && !paramsToFilter.contains(paramName)) {
			throw new ValidationException("Unknown parameter: " + parameter.getName() + " in node: "
					+ nodeInstanceName);
		}
	}

	@Override
	protected Map<String, ConnectorInstance> resolveCloudServices(Module module, User user,
																  Map<String, List<Parameter>> userChoices) {
		Map<String, ConnectorInstance> cloudServicesPerNode = new HashMap<String, ConnectorInstance>();
		ConnectorInstance cloudService = null;

		if (isProvidedUserChoicesForNodeInstance(userChoices, nodeInstanceName)) {
    		for (Parameter parameter : userChoices.get(nodeInstanceName)) {
    			if (parameter.getName().equals(RuntimeParameter.CLOUD_SERVICE_NAME)) {
    				cloudService = new ConnectorInstance(parameter.getValue(), null);
    				break;
    			}
    		}
		}

		if (cloudService == null || CloudService.isDefaultCloudService(cloudService.getName())) {
			cloudService = new ConnectorInstance(user.getDefaultCloudService(), null);
		}

		cloudServicesPerNode.put(nodeInstanceName, cloudService);

		return cloudServicesPerNode;
	}

	@Override
	protected void initExtraRunParameters(Module module, Run run) throws ValidationException {
	}

	@Override
	protected void updateExtraRunParameters(Module module, Run run, Map<String, List<Parameter>> userChoices)
			throws ValidationException {
	}

	@Override
    protected ImageModule castToRequiredModuleType(Module module) {
	    return (ImageModule) module;
    }

	@Override
	protected void postInitialize(Module module, Run run, User user) throws ValidationException, NotFoundException {
		super.postInitialize(module, run, user);

		initRuntimeParameters((ImageModule) module, run);
		initMachineState(run);

		String cloudService = run.getCloudServiceNameForNode(nodeInstanceName);
		initNodes(run, cloudService);

		initOrchestratorRuntimeParameters(run);
		initOrchestrators(run);

	}
}

