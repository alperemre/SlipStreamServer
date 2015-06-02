package com.sixsq.slipstream.persistence;

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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.*;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Text;

import com.sixsq.slipstream.exceptions.ValidationException;

import flexjson.JSON;

/**
 * Unit tests:
 * 
 * @see RuntimeParameterTest
 * 
 */
@Entity
@SuppressWarnings("serial")
@NamedQueries({
		@NamedQuery(name = "getParameterByInstanceId", query = "SELECT p FROM RuntimeParameter p WHERE p.key_ = 'instanceid' AND p.value = :instanceid"),
		@NamedQuery(name = "isSet", query = "SELECT p.isSet FROM RuntimeParameter p WHERE p.id = :resourceuri"),
		@NamedQuery(name = "getValueAndSet", query = "SELECT p.value, p.isSet FROM RuntimeParameter p WHERE p.id = :resourceuri"),
		@NamedQuery(name = "getValueByResourceUri", query = "SELECT p.value FROM RuntimeParameter p WHERE p.id = :resourceuri") })
public class RuntimeParameter extends Metadata {

	// Define the constants for properties:
	// Normal:
	// <nodename>:<property>
	// ss:<property>
	// Multiplicity:
	// <nodename>.<index>:<property>
	public final static String NODE_PROPERTY_SEPARATOR = ":";
	public final static String NODE_MULTIPLICITY_INDEX_SEPARATOR = ".";
	public final static String PARAM_WORD_SEPARATOR = ".";

	public final static String STATE_KEY = "state";
	public static final String STATE_DESCRIPTION = "Machine state";
	public static final String STATE_MESSAGE_DESCRIPTION = "Machine state message";
	public final static String STATE_CUSTOM_KEY = "statecustom";
	public static final String STATE_CUSTOM_DESCRIPTION = "Custom state";
	public static final String STATE_VM_KEY = "vmstate";
	public static final String STATE_VM_DESCRIPTION = "State of the VM, according to the cloud layer";

	public final static String ABORT_KEY = "abort";
	public final static String ABORT_DESCRIPTION = "Machine abort flag, set when aborting";

	public final static String GLOBAL_NAMESPACE = "ss";
	public final static String GLOBAL_NAMESPACE_PREFIX = GLOBAL_NAMESPACE + NODE_PROPERTY_SEPARATOR;

	public final static String GLOBAL_ABORT_KEY = GLOBAL_NAMESPACE_PREFIX + ABORT_KEY;
	public final static String GLOBAL_ABORT_DESCRIPTION = "Run abort flag, set when aborting";

	public final static String GLOBAL_STATE_KEY = GLOBAL_NAMESPACE_PREFIX + STATE_KEY;
	public final static String GLOBAL_STATE_DESCRIPTION = "Global execution state";

	public final static String GLOBAL_CATEGORY_KEY = GLOBAL_NAMESPACE_PREFIX + "category";

	public final static String GLOBAL_URL_SERVICE_KEY = GLOBAL_NAMESPACE_PREFIX + "url.service";
	public final static String GLOBAL_URL_SERVICE_DESCRIPTION = "Optional service URL for the deployment";

	public static final String TAGS_KEY = "tags";
	public static final String TAGS_DESCRIPTION = "Tags (comma separated) or annotations for this VM";

	public static final String GLOBAL_TAGS_KEY = GLOBAL_NAMESPACE_PREFIX + TAGS_KEY;
	public static final String GLOBAL_TAGS_DESCRIPTION = "Comma separated tag values";

	public static final String NODE_NODES_KEY = "nodes";
	public static final String GLOBAL_NODE_GROUPS_KEY = GLOBAL_NAMESPACE_PREFIX + NODE_NODES_KEY;
	public static final String GLOBAL_NODE_GROUPS_DESCRIPTION = "Comma separated nodes";

	public static final String COMPLETE_KEY = "complete";
	public static final String COMPLETE_DESCRIPTION = "'true' when current state is completed";

	public static final String GLOBAL_COMPLETE_KEY = GLOBAL_NAMESPACE_PREFIX + COMPLETE_KEY;
	public static final String GLOBAL_COMPLETE_DESCRIPTION = "Global complete flag, set when run completed";

	public final static String GLOBAL_RECOVERY_MODE_KEY = GLOBAL_NAMESPACE_PREFIX + "recovery.mode";
	public final static String GLOBAL_RECOVERY_MDDE_DESCRIPTION = "Run abort flag, set when aborting";

	public final static String IMAGE_ID_PARAMETER_NAME = "image.id";
	public final static String IMAGE_ID_PARAMETER_DESCRIPTION = "Cloud image id";

	public final static String IMAGE_PLATFORM_PARAMETER_NAME = "image.platform";
	public final static String IMAGE_PLATFORM_PARAMETER_DESCRIPTION = "Platform (eg: ubuntu, windows)";

	public final static String MULTIPLICITY_PARAMETER_NAME = "multiplicity";
	public final static String MULTIPLICITY_PARAMETER_DESCRIPTION = "Multiplicity number";

	public static final String MAX_PROVISIONING_FAILURES = "max-provisioning-failures";
	public static final String MAX_PROVISIONING_FAILURES_DESCRIPTION = "Max provisioning failures";

	public final static String IDS_PARAMETER_NAME = "ids";
	public final static String IDS_PARAMETER_DESCRIPTION = "IDs of the machines in a mutable deployment.";

	public static final String INSTANCE_ID_KEY = SpecialValues.instanceid.name();
	public static final String INSTANCE_ID_DESCRIPTION = "Cloud instance id";

	public static final String HOSTNAME_KEY = "hostname";
	public static final String HOSTNAME_DESCRIPTION = "hostname/ip of the image";

	public static final String CLOUD_SERVICE_NAME = "cloudservice";
	public static final String CLOUD_SERVICE_DESCRIPTION = "Cloud Service where the node resides";

	public static final String URL_SSH_KEY = "url.ssh";
	public static final String URL_SSH_DESCRIPTION = "SSH URL to connect to virtual machine";

	public static final String URL_SERVICE_KEY = "url.service";
	public static final String URL_SERVICE_DESCRIPTION = "Optional service URL for virtual machine";

	public static final String IS_ORCHESTRATOR_KEY = "is.orchestrator";
	public static final String IS_ORCHESTRATOR_DESCRIPTION = "True if it's an orchestrator";

	public static final String MAX_JAAS_WORKERS_KEY = "max.iaas.workers";
	public static final String MAX_JAAS_WORKERS_DESCRIPTION = "Max number of concurrently provisioned VMs by orchestrator";
	public static final String MAX_JAAS_WORKERS_DEFAULT = "20";

	public final static int MULTIPLICITY_NODE_START_INDEX = 1;

	private final static Pattern KEY_PATTERN = Pattern.compile("^(.*?):(.*)$");

	public final static String NODE_NAME_REGEX = "\\w+[\\w\\d]*";

	public final static Pattern NODE_NAME_ONLY_PATTERN = Pattern.compile("^(" + NODE_NAME_REGEX + ")*$");

	private final static String ORCHESTRATOR_INSTANCE_NAME_REGEX = Run.ORCHESTRATOR_NAME + "(-\\w[-\\w]*)?";

	private final static Pattern NODE_NAME_PART_PATTERN = Pattern.compile("(" + NODE_NAME_REGEX + "(\\.\\d+)?)|("
			+ RuntimeParameter.GLOBAL_NAMESPACE + ")|(" + ORCHESTRATOR_INSTANCE_NAME_REGEX + ")|(" + Run.MACHINE_NAME
			+ ")");

	private static final Pattern NAME_PATTERN = Pattern.compile("\\w[\\w\\d\\.-]*");

	public static final String NODE_NAME_KEY = "nodename";
	public static final String NODE_NAME_DESCRIPTION = "Nodename";
	public static final String NODE_ID_KEY = "id";
	public static final String NODE_ID_DESCRIPTION = "Node instance id";

	public enum ScaleStates {
		creating, created, operational, removing, removed, gone
	}

	public enum SpecialValues {
		instanceid
	}

	public static final String SCALE_STATE_KEY = "scale.state";
	public static final String SCALE_STATE_DEFAULT_VALUE = ScaleStates.creating.name();
	public static final String SCALE_STATE_DESCRIPTION = "Defined scalability state";

	public static final List<String> SPECIAL_PARAMETERS = Arrays.asList(RuntimeParameter.INSTANCE_ID_KEY);

	public static final String PRE_SCALE_DONE = "pre.scale.done";
	public static final String PRE_SCALE_DONE_DEFAULT_VALUE = "";
	public static final String PRE_SCALE_DONE_DESCRIPTION = "Pre-scale script ran or not.";

	public static final String SCALE_IAAS_DONE = "scale.iaas.done";
	public static final String SCALE_IAAS_DONE_DEFAULT_VALUE = "";
	public static final String SCALE_IAAS_DONE_DESCRIPTION = "Orchestrator completed scaling IaaS action on the node instance.";

	public static String extractNodeNamePart(String name) {
		if (!name.contains(NODE_PROPERTY_SEPARATOR)) {
			return null;
		}
		return name.split(NODE_PROPERTY_SEPARATOR)[0];
	}

	public static String extractParamNamePart(String name) {
		if (!name.contains(NODE_PROPERTY_SEPARATOR)) {
			return null;
		}
		try {
			return name.split(NODE_PROPERTY_SEPARATOR)[1];
		} catch (ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}

	public static String constructParamName(String nodeName, String paramname) {
		String prefix = nodeName + RuntimeParameter.NODE_PROPERTY_SEPARATOR;
		return prefix + paramname;
	}

	public static String constructParamName(String nodeName, int nodeInstanceId, String paramname) {
		String prefix = constructNodeInstanceName(nodeName, nodeInstanceId) + RuntimeParameter.NODE_PROPERTY_SEPARATOR;
		return prefix + paramname;
	}

	public static String constructNodeInstanceName(String nodeName, int nodeInstanceId) {
		return nodeName + RuntimeParameter.NODE_MULTIPLICITY_INDEX_SEPARATOR + nodeInstanceId;
	}

	@Version
	@JSON(include = false)
	private int jpaVersion;

	@Attribute(name = "key")
	private String key_;

	@Text(required = false, data = true)
	@Column(length = 4096)
	private String value = "";

	@Attribute
	private boolean isSet = false;

	@Attribute(required = false, name = "name")
	private String name_ = null;

	@Attribute(required = false, name = "group")
	private String group_ = "Global";

	@Attribute(required = false)
	private boolean mapsOthers;

	@Attribute(required = false)
	private ParameterType type = ParameterType.String;

	@Attribute(required = false)
	@Column(length = 65536)
	private String mappedRuntimeParameterNames = "";

	@ManyToOne
	@JSON(include = false)
	private Run container;

	/**
	 * Determines whether the given value is a real data value (string) or is a
	 * reference key to a runtime parameter in another node.
	 */
	@Attribute
	boolean isMappedValue = false;

	private RuntimeParameter() {
		super("Run");
	}

	public RuntimeParameter(Run run, String key, String value, String description) throws ValidationException {
		this();
		this.container = run;
		this.key_ = key;
		this.value = value;
		this.description = description;

		validate();
		init();
	}

	public boolean isSet() {
		return isSet;
	}

	// required by flexjson
	public boolean getIsSet() {
		return isSet();
	}

	public void setIsSet(boolean isSet) {
		this.isSet = isSet;
	}

	public void reset() {
		isSet = false;
		value = "";
	}

	public void validate() throws ValidationException {
		super.validate();

		if (container == null) {
			throwValidationException("runtime parameter container cannot be null");
		}

		if (this.key_ == null) {
			throwValidationException("runtime parameter key cannot be null or empty");
		}

		Matcher matcher = KEY_PATTERN.matcher(key_);
		if (!matcher.matches()) {
			String error = String.format("invalid runtime parameter name: %s.  Should match the following regex: %s",
					key_, KEY_PATTERN);
			throwValidationException(error);
		}

		String nodeNamePart = matcher.group(1);
		String keyNamePart = matcher.group(2);

		matcher = NODE_NAME_PART_PATTERN.matcher(nodeNamePart);
		if (!matcher.matches()) {
			throwValidationException("invalid node specification: " + nodeNamePart);
		}

		matcher = NAME_PATTERN.matcher(keyNamePart);
		if (!matcher.matches()) {
			throwValidationException("invalid parameter name specification: " + keyNamePart);
		}

	}

	private boolean isNullOrEmpty(String value) {
		return (value == null || "".equals(value));
	}

	private void init() {
		setId(container.getId() + "/" + key_);
		setIsSet(!isNullOrEmpty(value));
		group_ = RuntimeParameter.extractNodeNamePart(key_);
		if (GLOBAL_NAMESPACE.equals(group_)) {
			group_ = "Global";
		}
		setValue(value);
	}

	public boolean isMappedValue() {
		return isMappedValue;
	}

	public void setMappedValue(boolean isMappedValue) {
		this.isMappedValue = isMappedValue;
	}

	public Run getContainer() {
		return container;
	}

	void setContainer(Run container) {
		this.container = container;
	}

	public static RuntimeParameter loadFromUuidAndKey(String uuid, String key) {
		String id = getResourceUrl(uuid, key);
		return load(id);
	}

	private static String getResourceUrl(String uuid, String key) {
		String id = Run.RESOURCE_URI_PREFIX + uuid + "/" + key;
		return id;
	}

	public static RuntimeParameter load(String id) {
		EntityManager em = PersistenceUtil.createEntityManager();
		RuntimeParameter rp = em.find(RuntimeParameter.class, id);
		em.close();
		if(rp != null) {
			// The run pulled from the db is raw. We need to deserialize it.
			rp.setContainer((Run) rp.getContainer().substituteFromJson());
		}
		return rp;
	}

	public String getNodeName() {
		return key_.split(NODE_PROPERTY_SEPARATOR)[0];
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		setIsSet(!isNullOrEmpty(value));
		this.value = value;
		processValue();
	}

	private void processValue() {
		if (isMapsOthers()) {
			updateMappedRuntimeParameters();
		}
	}

	public void setGroup(String group) {
		this.group_ = group;
	}

	public String getGroup() {
		return group_;
	}

	public void setMapsOthers(boolean mapsOthers) {
		this.mapsOthers = mapsOthers;
	}

	public boolean isMapsOthers() {
		return mapsOthers;
	}

	public void setMappedRuntimeParameterNames(String mappedRuntimeParameters) {
		setMapsOthers(true);
		this.mappedRuntimeParameterNames = mappedRuntimeParameters;
	}

	public void addMappedRuntimeParameterName(String runtimeParameterName) {
		setMapsOthers(true);
		this.mappedRuntimeParameterNames += runtimeParameterName + ",";
	}

	public String getMappedRuntimeParameterNames() {
		return mappedRuntimeParameterNames;
	}

	private void updateMappedRuntimeParameters() {
		if (getContainer() == null) {
			return;
		}
		Map<String, RuntimeParameter> runtimeParameters = getContainer().getRuntimeParameters();
		for (String mappedRuntimaParameterName : getMappedRuntimeParameterNames().split(",")) {
			RuntimeParameter mappedRuntimeParameter = runtimeParameters.get(
					mappedRuntimaParameterName.trim());
			mappedRuntimeParameter.setValue(getValue());
		}
	}

	@SuppressWarnings("unchecked")
	public static List<RuntimeParameter> listRuntimeParameterByInstanceId(String instanceId) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("getParameterByInstanceId");
		q.setParameter("instanceid", instanceId);
		List<RuntimeParameter> list = q.getResultList();
		em.close();
		return list;
	}

	public void setType(ParameterType type) {
		this.type = type;
	}

	public ParameterType getType() {
		return type;
	}

	@Column
	public void setName(String name) {
		this.name_ = name;
	}

	@Column
	public String getName() {
		if (this.name_ == null) {
			this.name_ = extractParamNamePart(this.key_);
		}
		return this.name_;
	}

	public static boolean isAbort(String runId) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("isSet");
		q.setParameter("resourceuri", "run/" + runId + "/ss:abort");
		boolean res = (Boolean) q.getSingleResult();
		em.close();
		return res;
	}

	public RuntimeParameter store() {
		return (RuntimeParameter) super.store();
	}
}
