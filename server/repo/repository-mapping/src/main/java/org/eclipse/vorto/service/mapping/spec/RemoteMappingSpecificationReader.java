/**
 * Copyright (c) 2015-2016 Bosch Software Innovations GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * The Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Bosch Software Innovations GmbH - Please refer to git log
 */
package org.eclipse.vorto.service.mapping.spec;

import java.util.HashMap;

import org.apache.commons.jxpath.FunctionLibrary;
import org.eclipse.vorto.repository.api.IModelRepository;
import org.eclipse.vorto.repository.api.ModelId;
import org.eclipse.vorto.repository.api.content.FunctionblockModel;
import org.eclipse.vorto.repository.api.content.Infomodel;
import org.eclipse.vorto.repository.api.content.ModelProperty;
import org.eclipse.vorto.repository.api.content.Stereotype;
import org.eclipse.vorto.repository.client.RepositoryClientBuilder;
import org.eclipse.vorto.service.mapping.IMappingSpecification;
import org.eclipse.vorto.service.mapping.converters.JavascriptFunctions;

public class RemoteMappingSpecificationReader implements IMappingSpecificationReader {
	
	private IModelRepository repositoryClient = null;
	
	private String modelId;
	
	private String targetPlatformKey;
	
	private HashMap<String, JavascriptFunctions> namespaces = new HashMap<String, JavascriptFunctions>();
	private FunctionLibrary library = new FunctionLibrary();
	
	private static final String STEREOTYPE = "functions";
	private static final String ATT_NAMESPACE = "_namespace";

	private RemoteMappingSpecificationReader(IModelRepository repository) {
		this.repositoryClient = repository;
	}
	
	public static RemoteMappingSpecificationReader create() {
		return new RemoteMappingSpecificationReader(RepositoryClientBuilder.newBuilder().setBaseUrl("http://vorto.eclipse.org").buildModelRepositoryClient());
	}
	
	public RemoteMappingSpecificationReader remoteClient(IModelRepository repositoryClient) {
		this.repositoryClient = repositoryClient;
		return this;
	}
	
	public RemoteMappingSpecificationReader infomodelId(String modelId) {
		this.modelId = modelId;
		return this;
	}
	
	public RemoteMappingSpecificationReader targetPlatformKey(String key) {
		this.targetPlatformKey = key;
		return this;
	}
	
	@Override
	public IMappingSpecification read() {
		
		try {
			Infomodel infomodel = this.repositoryClient.getContent(ModelId.fromPrettyFormat(this.modelId), Infomodel.class, this.targetPlatformKey).get();
			if (infomodel == null) {
				throw new CannotReadSpecificationProblem("Information Model cannot be found with given model ID and/or key");
			}
			
			DefaultMappingSpecification specification = new DefaultMappingSpecification();
			specification.setInfomodel(infomodel);
			
			for (ModelProperty fbProperty : infomodel.getFunctionblocks()) {
				ModelId fbModelId = (ModelId)fbProperty.getType();
				FunctionblockModel fbm = this.repositoryClient.getContent(fbModelId, FunctionblockModel.class,this.targetPlatformKey).get();
				
				if (fbm != null) {
					if (fbm.getStereotype(STEREOTYPE).isPresent()) {
						JavascriptFunctions functions;
						Stereotype functionsStereotype = fbm.getStereotype(STEREOTYPE).get();
						String namespace = functionsStereotype.getAttributes().get(ATT_NAMESPACE);
						if (namespaces.containsKey(namespace)) {
							functions = namespaces.get(namespace);
						} else {
							functions = new JavascriptFunctions(namespace);
							this.library.addFunctions(functions);
						}
						
						for (String key : functionsStereotype.getAttributes().keySet()) {
							if (!ATT_NAMESPACE.equalsIgnoreCase(key)) {
								functions.addFunction(key,functionsStereotype.getAttributes().get(key));
							}
						}
					}
				} else {
					fbm = this.repositoryClient.getContent(fbModelId, FunctionblockModel.class).get();
				}
				specification.getFbs().put(fbProperty.getName(), fbm);
			}
			
			specification.setLibrary(this.library);
			return specification;
		} catch (Exception e) {
			throw new CannotReadSpecificationProblem("Problem reading mapping specification", e);
		}	
	}

}
