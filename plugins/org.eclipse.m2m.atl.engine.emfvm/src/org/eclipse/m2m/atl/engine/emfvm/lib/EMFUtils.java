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

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.m2m.atl.engine.emfvm.EmfvmPlugin;

public class EMFUtils {
	
	// TODO: map this to allowInterModelReferences option
	private static boolean allowInterModelReferences = false;

	protected static Logger logger = Logger.getLogger(EmfvmPlugin.LOGGER);
	
	public static Object get(StackFrame frame, EObject eo, String name) {
		Object ret = null;
		
		EClass ec = eo.eClass();
		try {
			if("__xmiID__".equals(name)) {
				ret = frame.execEnv.getModelOf(eo).resource.getURIFragment(eo);
			} else {
				EStructuralFeature sf = ec.getEStructuralFeature(name);
				Object val = eo.eGet(sf);
				if(val == null) val = OclUndefined.SINGLETON;
				ret = val;
			}
		} catch(Exception e) {
			throw new VMException(frame, "error accessing " + ec + "." + name);
		}

		return ret;
	}

	// TODO:
	//	- EEnumliteral implementation
	//		- could be different (faster?) when same metamodel in source and target
	//		- may be too permissive (any value for which toString returns a valid literal name works) 
	//	- should flatten nested collections
	public static void set(StackFrame frame, EObject eo, String name, Object value) {
		final boolean debug = false;
		EStructuralFeature feature = eo.eClass().getEStructuralFeature(name);
		
		// makes it possible to use an integer to set a floating point property  
		if(value instanceof Integer) {
			String targetType = feature.getEType().getInstanceClassName();
			if("java.lang.Double".equals(targetType) || "java.lang.Float".equals(targetType)) {
				value = new Double(((Integer)value).doubleValue());
			}
		}
		
		EClassifier type = feature.getEType();
		boolean targetIsEnum = type instanceof EEnum;
		try {
			Object oldValue = eo.eGet(feature);
			if(oldValue instanceof Collection) {
				Collection oldCol = (Collection)oldValue;
				if(value instanceof Collection) {
					if(targetIsEnum) {
						EEnum eenum = (EEnum)type;
						for(Iterator i = ((Collection)value).iterator() ; i.hasNext() ; ) {
							Object v = i.next();
							oldCol.add(eenum.getEEnumLiteral(v.toString()));							
						}
					} else if(allowInterModelReferences) {
						oldCol.addAll((Collection)value);
					} else {	// !allowIntermodelReferences
						for(Iterator i = ((Collection)value).iterator() ; i.hasNext() ; ) {
							Object v = i.next();
							if(v instanceof EObject) {
								if(frame.execEnv.getModelOf(eo) == frame.execEnv.getModelOf((EObject)v))
									oldCol.add(v);
								else if (debug)
									logger.warning("Refusing to set inter-model reference to " + feature);
							} else {
								oldCol.add(v);								
							}
						}
					}
				} else {
					if(targetIsEnum) {
						EEnum eenum = (EEnum)type;
						oldCol.add(eenum.getEEnumLiteral(value.toString()));							
					} else if(allowInterModelReferences || !(value instanceof EObject)) {
						oldCol.add(value);
					} else {	// (!allowIntermodelReferences) && (value intanceof EObject)
						if(frame.execEnv.getModelOf(eo) == frame.execEnv.getModelOf((EObject)value))
							oldCol.add(value);
						else if (debug)
							logger.warning("Refusing to set inter-model reference to " + feature);
					}
				}
			} else {
				if(value instanceof Collection) {
					logger.warning("Assigning a Collection to a single-valued feature");
					Collection c = (Collection)value;
					if(!c.isEmpty()) {
						value = c.iterator().next();
					} else {
						value = null;
					}
				}
				if(targetIsEnum) {
					EEnum eenum = (EEnum)type;
					eo.eSet(feature, eenum.getEEnumLiteral(value.toString()).getInstance());							
				} else if(allowInterModelReferences || !(value instanceof EObject)) {
					eo.eSet(feature, value);
				} else {	// (!allowIntermodelReferences) && (value instanceof EObject)
					if(frame.execEnv.getModelOf(eo) == frame.execEnv.getModelOf((EObject)value))
						eo.eSet(feature, value);
					else if (debug)
						logger.warning("Refusing to set inter-model reference to " + feature);
				}
			}
		} catch(Exception e) {
			logger.log(
					Level.WARNING, 
					"Could not assign " + value + " to " + 
					frame.execEnv.toPrettyPrintedString(eo) + "." + name, 
					e);
		}
	}

	public static void setAllowInterModelReferences(
			boolean allowInterModelReferences) {
		EMFUtils.allowInterModelReferences = allowInterModelReferences;
	}
}
