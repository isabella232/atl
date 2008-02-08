/*******************************************************************************
 * Copyright (c) 2007 INRIA.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Fr�d�ric Jouault - initial API and implementation
 *******************************************************************************/
package org.eclipse.m2m.atl.engine.emfvm.lib;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.m2m.atl.engine.AtlEMFModelHandler;

public class ReferenceModel extends Model {
	
	private Map metaElementByName = null;
	private Set referencedResources = new HashSet();

	// nsURIs that were explicitly registered and need unregistering
	protected Set unregister = new HashSet();
	
	public void dispose() {
        referencedResources.clear();
		metaElementByName = null;
		// unregister packages
        for (Iterator unrs = unregister.iterator(); unrs.hasNext();) {
        	String nsURI = (String)unrs.next();
        	synchronized (resourceSet) {
            	resourceSet.getPackageRegistry().remove(nsURI);
        	}
        }
        // take resource out of (static) resourceSet
        synchronized (resourceSet) {
            resourceSet.getResources().remove(resource);
        }
		super.dispose();
	}

	// TODO: why require the referenceModel to be passed as argument when we know it is the mmm
	public ReferenceModel(ReferenceModel referenceModel, URL url) throws IOException {
		super(referenceModel, url, false);
		registerPackages();
		adapt();
		addAllReferencedResources(this.resource);
	}

	public ReferenceModel(ReferenceModel referenceModel, URI uri) throws IOException {
		super(referenceModel, uri, false);
		registerPackages();
		adapt();
		addAllReferencedResources(this.resource);
	}

	public ReferenceModel(ReferenceModel referenceModel, Resource resource) {
		super(referenceModel, resource);
		registerPackages();
		addAllReferencedResources(this.resource);
	}

	// TODO:
	//	- current implementation uses the regular VM package registry because it is used by the launch config
	//	future version may use EPackage.Registry.INSTANCE.getEPackage(uri).eResource()
	//	It may even be better to have a common package registry for all VMs
	public ReferenceModel(ReferenceModel referenceModel, String uri) {
		super(referenceModel, AtlEMFModelHandler.getResourceSet().getPackageRegistry().getEPackage(uri).eResource());
		registerPackages();
		addAllReferencedResources(this.resource);
	}
	
	private void registerPackages() {
		for(Iterator i = getElementsByType(EcorePackage.eINSTANCE.getEPackage()).iterator() ; i.hasNext() ; ) {
			EPackage p = (EPackage)i.next();
			String nsURI = p.getNsURI();
			if(nsURI == null) {
				nsURI = p.getName() ;
				p.setNsURI(nsURI);
			}
			if (!resourceSet.getPackageRegistry().containsKey(nsURI)) {
				unregister.add(nsURI);
			}
			synchronized (resourceSet) {
				resourceSet.getPackageRegistry().put(nsURI, p);
			}
		}		
	}

	private void adapt() {
		for(Iterator i = getElementsByType(EcorePackage.eINSTANCE.getEDataType()).iterator() ; i.hasNext() ; ) {
			EDataType dt = (EDataType)i.next();
			String tname = dt.getName();
			String icn = null;
			if(tname.equals("Boolean")) {
				icn = "boolean";	//"java.lang.Boolean";
			} else if(tname.equals("Double")) {
				icn = "java.lang.Double";
			} else if(tname.equals("Float")) {
				icn = "java.lang.Float";
			} else if(tname.equals("Integer")) {
				icn = "java.lang.Integer";
			} else if(tname.equals("String")) {
				icn = "java.lang.String";
			}
			if(icn != null)
				dt.setInstanceClassName(icn);
		}
	}

	private ReferenceModel(Resource resource) {
		super(resource);
	}

	public EClass getMetaElementByName(String name) {
		if(metaElementByName == null) {
			synchronized (this) {
				if(metaElementByName == null) {
					// check again, since another locking thread may have initialised 'classifiers'
					metaElementByName = initMetaElementsInAllResources();
				}
			}
//			metaElementByName = new HashMap();
//			for(Iterator i = getElementsByType(EcorePackage.eINSTANCE.getEClass()).iterator() ; i.hasNext() ; ) {
//				EClass metaElement = (EClass)i.next();
//				metaElementByName.put(metaElement.getName(), metaElement);
//			}
		}
		
		return (EClass)metaElementByName.get(name);
	}
	
    /**
     * Indexes all EClasses in main resource and
     * referenced resources.
     * @return Map of names to EClasses
     * @see #register(Map, String, EObject)
     * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
     */
	private Map initMetaElementsInAllResources() {
    	Map metaElementByName = new HashMap();
    	initMetaElements(metaElementByName, resource.getContents().iterator(), null);
        Iterator refResources = referencedResources.iterator();
        while (refResources.hasNext()) {
            initMetaElements(
            		metaElementByName,
                    ((Resource)refResources.next()).getContents().iterator(), 
                    null);
        }
        return metaElementByName;
	}
	
	private static void initMetaElements(Map metaElementByName, Iterator i, String base) {
		for( ; i.hasNext() ; ) {
			EObject eo = (EObject)i.next();
			if(eo instanceof EPackage) {
				String name = ((EPackage)eo).getName();
				if(base != null) {
					name = base + "::" + name;
				}
				initMetaElements(metaElementByName, ((EPackage)eo).eContents().iterator(), name);
			} else if(eo instanceof EClassifier) {
				String name = ((EClassifier)eo).getName();
				// register the classifier under its simple name
				register(metaElementByName, name, eo);
				if(base != null) {
					name = base + "::" + name;
					// register the classifier under its full name
					register(metaElementByName, name, eo);
				}
			} else {
                // No meta-package or meta-class => just keep digging.
                // N.B. This situation occurs in UML2 profiles, where
                // EPackages containing EClasses are buried somewhere
                // underneath other elements.
                initMetaElements(metaElementByName, eo.eContents().iterator(), base);
            }
		}
	}
	
	private static void register(Map metaElementByName, String name, EObject classifier) {
		if(metaElementByName.containsKey(name)) {
			logger.warning("metamodel contains several classifiers with same name: " + name);
		}
		metaElementByName.put(name, classifier);
	}

    /**
     * Searches for and adds all resources that are
     * referenced from the main resource to referencedResources.
     * @param The main resource
     * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
     */
    protected void addAllReferencedResources(Resource resource) {
        Iterator contents = resource.getAllContents();
        while (contents.hasNext()) {
            Object o = contents.next();
            if (o instanceof EClass) {
                addReferencedResourcesFor((EClass)o, new HashSet());
            }
        }
        referencedResources.remove(resource);
    }
    
    /**
     * Searches for and adds all resources that are
     * referenced from eClass to referencedResources.
     * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
     * @param eClass
     * @param ignore Set of classes to ignore for searching.
     */
    private void addReferencedResourcesFor(EClass eClass, Set ignore) {
		if (ignore.contains(eClass)) {
			return;
		}
		ignore.add(eClass);
		Iterator eRefs = eClass.getEReferences().iterator();
		while (eRefs.hasNext()) {
			EReference eRef = (EReference) eRefs.next();
			if (eRef.isContainment()) {
				EClassifier eType = eRef.getEType();
				if (eType.eResource() != null) {
					referencedResources.add(eType.eResource());
				} else {
					logger.warning("Resource for " + eType.toString()
							+ " is null; cannot be referenced");
				}
				if (eType instanceof EClass) {
					addReferencedResourcesFor((EClass) eType, ignore);
				}
			}
		}
		Iterator eAtts = eClass.getEAttributes().iterator();
		while (eAtts.hasNext()) {
			EAttribute eAtt = (EAttribute) eAtts.next();
			EClassifier eType = eAtt.getEType();
			if (eType.eResource() != null) {
				referencedResources.add(eType.eResource());
			} else {
				logger.warning("Resource for " + eType.toString()
						+ " is null; cannot be referenced");
			}
		}
		Iterator eSupers = eClass.getESuperTypes().iterator();
		while (eSupers.hasNext()) {
			EClass eSuper = (EClass) eSupers.next();
			if (eSuper.eResource() != null) {
				referencedResources.add(eSuper.eResource());
				addReferencedResourcesFor(eSuper, ignore);
			} else {
				logger.warning("Resource for " + eSuper.toString()
						+ " is null; cannot be referenced");
			}
		}
	}
    
    /**
	 * @return The set of referenced Resources.
	 */
    public Set getReferencedResources() {
        return referencedResources;
    }

    /**
     * @param object The model element.
     * @return True if object is contained in this model's resource or a referenced resource.
     * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
     */
    public boolean isModelOf(EObject object) {
    	final Resource res = object.eResource();
    	if (res.equals(resource)) {
    		return true;
    	}
    	for (Iterator i = referencedResources.iterator(); i.hasNext();) {
    		if (res.equals(i.next())) {
    			return true;
    		}
    	}
    	return false;
    }

    public static ReferenceModel getMetametamodel() {
		if(metametamodel == null) {
			metametamodel = new ReferenceModel(EcorePackage.eINSTANCE.eResource());
		}
		return metametamodel;
	}
	
	private static ReferenceModel metametamodel;
}
