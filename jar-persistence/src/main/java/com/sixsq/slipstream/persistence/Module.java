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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.OneToOne;
import javax.persistence.Query;
import javax.persistence.Transient;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementMap;

import com.sixsq.slipstream.exceptions.SlipStreamClientException;
import com.sixsq.slipstream.exceptions.SlipStreamRuntimeException;
import com.sixsq.slipstream.exceptions.ValidationException;
import com.sixsq.slipstream.module.ModuleVersionView;
import com.sixsq.slipstream.module.ModuleView;
import com.sixsq.slipstream.run.RunViewList;
import com.sixsq.slipstream.util.ModuleUriUtil;
import com.sixsq.slipstream.util.SerializationUtil;

import flexjson.JSON;
import flexjson.JSONDeserializer;

/**
 * Unit test see:
 * 
 * @see ModuleTest
 * 
 */
@SuppressWarnings("serial")
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@NamedQueries({
		@NamedQuery(name = "moduleLastVersion", query = "SELECT m FROM Module m WHERE m.version = (SELECT MAX(n.version) FROM Module n WHERE n.name = :name AND n.deleted != TRUE)"),
		@NamedQuery(name = "moduleViewLatestChildren", query = "SELECT NEW com.sixsq.slipstream.module.ModuleView(m.id, m.description, m.category, m.customVersion, m.authz) FROM Module m WHERE m.parentUri = :parent AND m.version = (SELECT MAX(c.version) FROM Module c WHERE c.name = m.name AND c.deleted != TRUE)"),
		@NamedQuery(name = "moduleViewAllVersions", query = "SELECT NEW com.sixsq.slipstream.module.ModuleVersionView(m.id, m.version, m.updated, m.commit, m.authz, m.category) FROM Module m WHERE m.name = :name AND m.deleted != TRUE"),
		@NamedQuery(name = "moduleAll", query = "SELECT m FROM Module m WHERE m.deleted != TRUE"),
		@NamedQuery(name = "moduleViewPublished", query = "SELECT NEW com.sixsq.slipstream.module.ModuleViewPublished(m.id, m.description, m.category, m.customVersion, m.authz, m.logoLink) FROM Module m WHERE m.isPublished = TRUE AND m.deleted != TRUE") })
public abstract class Module extends Parameterized implements Guarded {

	public final static String RESOURCE_URI_PREFIX = "module/";

	public final static int DEFAULT_VERSION = -1;

	@Attribute(required = false)
	protected ModuleCategory category;

	private static void bindModuleToAuthz(Module module) {
		if (module != null) {
			// set authz to bind them
			module.getAuthz().setGuarded(module);
		}
	}

	private static Module loadByUri(String uri) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Module m = (Module) Metadata.load(uri, Module.class);
		Module latestVersion = loadLatest(uri);
		em.close();
		if (m != null) {
			m = postLoad(m, latestVersion);
		}
		bindModuleToAuthz(m);
		return m;
	}

	private static Module postLoad(Module m, Module latestVersion) {
		m = (Module) m.substituteFromJson();
		if (latestVersion != null) {
			m.setIsLatestVersion(latestVersion.version);
		}
		return m;
	}

	public static Module loadLatest(String id) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("moduleLastVersion");
		String name = ModuleUriUtil.extractModuleNameFromResourceUri(id);
		q.setParameter("name", name);
		Module module;
		try {
			module = (Module) q.getSingleResult();
			module = (Module) module.substituteFromJson();
			module.setIsLatestVersion(module.version);
		} catch (NoResultException ex) {
			module = null;
		}
		em.close();
		bindModuleToAuthz(module);
		return module;
	}

	public static boolean exists(String id) {
		boolean exists;
		if (load(id) != null) {
			exists = true;
		} else {
			exists = false;
		}
		return exists;
	}

	public static Module loadByName(String name) {
		return load(constructResourceUri(name));
	}

	public static Module load(String uri) {
		String id = uri;
		int version = ModuleUriUtil.extractVersionFromResourceUri(id);
		Module module = (version == DEFAULT_VERSION ? loadLatest(id) : loadByUri(id));
		return module;
	}

	@SuppressWarnings("unchecked")
	public static List<Module> listAll() {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("moduleAll");
		List<Module> list = q.getResultList();
		em.close();
		return list;
	}

	@SuppressWarnings("unchecked")
	public static List<ModuleView> viewList(String id) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("moduleViewLatestChildren");
		q.setParameter("parent",
				Module.constructResourceUri(ModuleUriUtil.extractModuleNameFromResourceUri(id)));
		List<ModuleView> list = q.getResultList();
		em.close();
		return list;
	}

	@SuppressWarnings("unchecked")
	public static List<ModuleVersionView> viewListAllVersions(String id) {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("moduleViewAllVersions");
		String name = ModuleUriUtil.extractModuleNameFromResourceUri(id);
		q.setParameter("name", name);
		List<ModuleVersionView> list = q.getResultList();
		em.close();
		return list;
	}

	@SuppressWarnings("unchecked")
	public static List<ModuleView> viewPublishedList() {
		EntityManager em = PersistenceUtil.createEntityManager();
		Query q = em.createNamedQuery("moduleViewPublished");
		List<ModuleView> list = q.getResultList();
		em.close();
		return list;
	}

	public static String constructResourceUri(String name) {
		return RESOURCE_URI_PREFIX + name;
	}

	public static String constructResourceUrl(String name, int version) {
		return constructResourceUri(name + "/" + String.valueOf(version));
	}

	@SuppressWarnings("unchecked")
	public static Module fromJson(String json, Class<? extends Module> type) throws SlipStreamClientException {
		Method m;
		try {
			m = type.getMethod("createDeserializer");
		} catch (NoSuchMethodException e) {
			throw new SlipStreamRuntimeException(e);
		} catch (SecurityException e) {
			throw new SlipStreamRuntimeException(e);
		}
		String[] params = null;
		JSONDeserializer<Object> deserializer;
		try {
			deserializer = (JSONDeserializer<Object>) m.invoke(null, (Object) params);
		} catch (IllegalAccessException e) {
			throw new SlipStreamRuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new SlipStreamRuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new SlipStreamRuntimeException(e);
		}
		return (Module) SerializationUtil.fromJson(json, type, deserializer);
	}

	@Element(required = false)
	@Embedded
	private Authz authz = new Authz("", this);

	@Transient
	@JSON(include = false)
	private Module parentModule = null;

	@Attribute(required = false)
	private String customVersion;

	/**
	 * Module hierarchy (e.g. <parent>/<module>)
	 */
	@JSON(include = false)
	private String parentUri;

	/**
	 * Last part of the module hierarchy (e.g. <parent>/<module>)
	 */
	@Attribute(required = true)
	private String name;

	@Attribute(required = true)
	private int version;

	/**
	 * Intended to inform users about constraints or requirements on the module
	 * requirements - e.g. required ports.
	 */
	@Transient
	@Element(required = false)
	private String note;

	@Transient
	@Attribute(required = false)
	private boolean isLatestVersion;

	@Transient
	@Attribute(required = false)
	private String tag;

	@Element(required = false)
	@OneToOne(optional = true, cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private Commit commit;

	@Attribute
	@Column(nullable = true)
	private Boolean isPublished = false; // to the app store

	@Transient
	@Element(required = false)
	private Publish published; // to the app store

	/**
	 * Module reference is a URI.
	 * <p/>
	 * In the case "Image", the module to use as a base image from which to
	 * extract the cloud image id (e.g. AMI). It can be empty if the ImageId
	 * field is provided.
	 */
	@Attribute(required = false)
	private String moduleReferenceUri;

	/**
	 * Contains all available cloud services. Used for HTML UI.
	 */
	@Transient
	@ElementArray(required = false)
	protected String[] cloudNames;

	@Transient
	@Element(required = false)
	private RunViewList runs;

	@Attribute(required = false)
	private String logoLink;

	protected Module(ModuleCategory category) {
		super(category.toString());
		this.category = category;
		isPublished = false;
	}

	public Module(String name, ModuleCategory category) throws ValidationException {
		this(category);
		setName(name);

		validateName(name);

		setId(constructResourceUri(name));

		extractUriComponents();
	}

	protected JSONDeserializer<Object> createDeserializer() {
		return new JSONDeserializer<Object>().use("parameters.values", ModuleParameter.class).use(
				"nodes.values.image.parameters.values", ModuleParameter.class);
	}

	@Override
	@ElementMap(name = "parameters", required = false, valueType = ModuleParameter.class)
	protected void setParameters(Map<String, Parameter> parameters) {
		this.parameters = parameters;
	}

	@Override
	@ElementMap(name = "parameters", required = false, valueType = ModuleParameter.class)
	public Map<String, Parameter> getParameters() {
		return parameters;
	}

	@JSON(include = false)
	public Guarded getGuardedParent() {
		if (parentModule == null) {
			if (parentUri != null) {
				parentModule = Module.load(parentUri);
			}
		}
		return parentModule;
	}

	public void clearGuardedParent() {
		parentModule = null;
	}

	public RunViewList getRuns() {
		return runs;
	}

	public void setRuns(RunViewList runs) {
		this.runs = runs;
	}

	private void validateName(String name) throws ValidationException {
		if (name == null || "".equals(name)) {
			throw new ValidationException("module name cannot be empty");
		}
		if (ModuleUriUtil.extractVersionFromResourceUri(name) != -1) {
			throw new ValidationException("Invalid name, cannot be an integer");
		}
		if (name.contains(" ")) {
			throw new ValidationException("Invalid name, cannot contain space");
		}
	}

	@Attribute
	public String getShortName() {
		String name;
		name = getId() == null ? null : ModuleUriUtil.extractShortNameFromResourceUri(getId());
		return name;
	}

	@Attribute
	public void setShortName(String name) {
	}

	public String getOwner() {
		return authz.getUser();
	}

	public Authz getAuthz() {
		return authz;
	}

	public void setAuthz(Authz authz) {
		this.authz = authz;
	}

	public int getVersion() {
		return version;
	}

	/**
	 * Needed for json deserialization
	 */
	@SuppressWarnings("unused")
	private void setVersion(int version) {
		this.version = version;
	}

	@Override
	@Attribute(required = true, name = "parentUri")
	@JSON
	public String getParent() {
		return parentUri;
	}

	@Attribute(required = true, name = "parentUri")
	@JSON
	private void setParent(String parentUri) {
		this.parentUri = parentUri;
	}

	@Override
	public String getName() {
		return name;
	}

	/*
	 * Should not be called directly
	 * 
	 * @see com.sixsq.slipstream.persistence.Metadata#setName(java.lang.String)
	 */
	@Override
	public void setName(String name) throws ValidationException {
		this.name = name;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public String getCustomVersion() {
		return customVersion;
	}

	public void setCustomVersion(String customVersion) {
		this.customVersion = customVersion;
	}

	public String getModuleReferenceUri() {
		return moduleReferenceUri;
	}

	public void setModuleReferenceUri(String moduleReferenceUri) {
		this.moduleReferenceUri = moduleReferenceUri;
	}

	public void setModuleReference(Module reference) throws ValidationException {
		if(reference != null) {
			setModuleReference(reference.getId());
		}
	}

	public void setModuleReference(String moduleReferenceUri) throws ValidationException {
		if (moduleReferenceUri != null) {
			String moduleReferenceUriVersionLess = ModuleUriUtil.extractVersionLessResourceUri(moduleReferenceUri);
			String moduleUriVersionLess = ModuleUriUtil.extractVersionLessResourceUri(getId());
			if (moduleUriVersionLess.equals(moduleReferenceUriVersionLess)) {
				throw new ValidationException("Module reference cannot be itself");
			}
		}
		this.moduleReferenceUri = moduleReferenceUri;
	}

	/**
	 * A virtual module is a module that doesn't require to be explicitly built,
	 * since it only defines runtime behavior
	 * 
	 * @return true if the module is virtual, false if not
	 */
	public boolean isVirtual() {
		return false;
	}

	private void extractUriComponents() throws ValidationException {

		version = ModuleUriUtil.extractVersionFromResourceUri(getId());

		parentUri = ModuleUriUtil.extractParentUriFromResourceUri(getId());

		name = ModuleUriUtil.extractModuleNameFromResourceUri(getId());
	}

	@Override
	public void setContainer(Parameter parameter) {
		parameter.setContainer(this);
	}

	public Module store() {
		return store(true);
	}

	public Module store(boolean incrementVersion) {
		setUpdated();
		if (incrementVersion) {
			setVersion();
		}
		return (Module) super.store();
	}

	protected void setVersion() {
		version = VersionCounter.getNextVersion();
		setId(Module.constructResourceUri(ModuleUriUtil.extractModuleNameFromResourceUri(getId()) + "/"
				+ version));
	}

	protected void setIsLatestVersion(int lastVersion) {
		this.isLatestVersion = version == lastVersion;
	}

	public void setCommit(String author, String commit) {
		this.commit = new Commit(author, commit, this);
	}

	public void setCommit(Commit commit) {
		this.commit = commit;
	}

	public Commit getCommit() {
		return commit;
	}

	public String[] getCloudNames() {
		return cloudNames;
	}

	public void setCloudNames(String[] cloudNames) {
		this.cloudNames = cloudNames;
	}

	public String getLogoLink() {
		return logoLink;
	}

	public void setLogoLink(String logoLink) {
		this.logoLink = logoLink;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	/**
	 * Retreive the default parameter from the inheritance hierarchy
	 * 
	 * @param parameterName
	 * @return
	 * @throws ValidationException
	 */
	public String getInheritedDefaultParameterValue(String parameterName) throws ValidationException {
		return "";
	}

	public void publish() {
		published = new Publish(this);
		isPublished = true;
	}

	public void unpublish() {
		published = null;
		isPublished = false;
	}

	@Transient
	@JSON
	public Publish getPublished() {
		return published;
	}

	@Transient
	@JSON
	private void setPublished(Publish published) {
		if (published != null) {
			published.setModule(this);
		}
		this.published = published;
		isPublished = (published != null);
	}

	protected String computeParameterValue(String key) throws ValidationException {
		Parameter parameter = getParameter(key);
		String value = (parameter == null ? null : parameter.getValue());
		if (value == null) {
			String reference = getModuleReferenceUri();
			if (reference != null) {
				Module parent = Module.load(getModuleReferenceUri());
				if (parent != null) {
					value = parent.computeParameterValue(key);
				}
			}
		}
		return value;
	}

	public abstract Module copy() throws ValidationException;

	protected Module copyTo(Module copy) throws ValidationException {
		copy = (Module) super.copyTo(copy);

		copy.setCategory(getCategory());

		if (getCommit() != null) {
			copy.setCommit(getCommit().copy());
		}
		copy.setCustomVersion(getCustomVersion());

		copy.setCloudNames((cloudNames == null ? null : cloudNames.clone()));
		copy.setModuleReference(getModuleReferenceUri());
		copy.setTag(getTag());
		copy.setLogoLink(getLogoLink());

		copy.setAuthz(getAuthz().copy(copy));

		return copy;
	}

	public ModuleCategory getCategory() {
		return category;
	}

	public void setCategory(ModuleCategory category) {
		this.category = category;
	}

	public void setCategory(String category) {
		this.category = ModuleCategory.valueOf(category);
	}
}
