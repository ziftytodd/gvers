package com.supersites;

import grails.converters.JSON;

import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.support.proxy.DefaultProxyHandler;
import org.codehaus.groovy.grails.support.proxy.ProxyHandler;
import org.codehaus.groovy.grails.web.converters.ConverterUtil;
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException;
import org.codehaus.groovy.grails.web.converters.marshaller.ObjectMarshaller;
import org.codehaus.groovy.grails.web.json.JSONWriter;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

/**
 * Created by IntelliJ IDEA.
 * User: todd
 * Date: Jun 24, 2010
 * Time: 9:16:13 AM
 * To change this template use File | Settings | File Templates.
 */
public class MyDomainClassMarshaller implements ObjectMarshaller<JSON> {
    private boolean includeVersion = false;
    private ProxyHandler proxyHandler;

    public MyDomainClassMarshaller(boolean includeVersion) {
        this(includeVersion, new DefaultProxyHandler());
    }

    public MyDomainClassMarshaller(boolean includeVersion, ProxyHandler proxyHandler) {
        this.includeVersion = includeVersion;
        this.proxyHandler = proxyHandler;
    }


    public boolean isIncludeVersion() {
        return includeVersion;
    }

    public void setIncludeVersion(boolean includeVersion) {
        this.includeVersion = includeVersion;
    }

    public boolean supports(Object object) {
        return ConverterUtil.isDomainClass(object.getClass());
    }

    public void marshalObject(Object value, JSON json) throws ConverterException {
        JSONWriter writer = json.getWriter();
        value = proxyHandler.unwrapIfProxy(value);
        Class clazz = value.getClass();
        GrailsDomainClass domainClass = ConverterUtil.getDomainClass(clazz.getName());
        BeanWrapper beanWrapper = new BeanWrapperImpl(value);

        writer.object();
        writer.key("class").value(domainClass.getClazz().getName());

        GrailsDomainClassProperty[] properties = domainClass.getPersistentProperties();

        for (GrailsDomainClassProperty property : properties) {
            writer.key(property.getName());
            if (!property.isAssociation()) {
                // Write non-relation property
                Object val = beanWrapper.getPropertyValue(property.getName());
                json.convertAnother(val);
            } else {
                Object referenceObject = beanWrapper.getPropertyValue(property.getName());
                if (isRenderDomainClassRelations()) {
                    if (referenceObject == null) {
                        writer.value(null);
                    } else {
                    	referenceObject = proxyHandler.unwrapIfProxy(referenceObject);
                        if (referenceObject instanceof SortedMap) {
                            referenceObject = new TreeMap((SortedMap) referenceObject);
                        } else if (referenceObject instanceof SortedSet) {
                            referenceObject = new TreeSet((SortedSet) referenceObject);
                        } else if (referenceObject instanceof Set) {
                            referenceObject = new HashSet((Set) referenceObject);
                        } else if (referenceObject instanceof Map) {
                            referenceObject = new HashMap((Map) referenceObject);
                        } else if (referenceObject instanceof Collection){
                            referenceObject = new ArrayList((Collection) referenceObject);
                        }
                        json.convertAnother(referenceObject);
                    }
                } else {
                    if (referenceObject == null) {
                        json.value(null);
                    } else {
                        GrailsDomainClass referencedDomainClass = property.getReferencedDomainClass();

                        // Embedded are now always fully rendered
                        if(referencedDomainClass == null || property.isEmbedded() || GrailsClassUtils.isJdk5Enum(property.getType())) {
                            json.convertAnother(referenceObject);
                        } else if (property.isOneToOne() || property.isManyToOne() || property.isEmbedded()) {
                            asShortObject(referenceObject, json, referencedDomainClass.getIdentifier(), referencedDomainClass.getVersion(), referencedDomainClass);
                        } else {
                            GrailsDomainClassProperty referencedIdProperty = referencedDomainClass.getIdentifier();
                            GrailsDomainClassProperty referencedVersionProperty = referencedDomainClass.getVersion();
                            @SuppressWarnings("unused")
							String refPropertyName = referencedDomainClass.getPropertyName();
                            if (referenceObject instanceof Collection) {
                                Collection o = (Collection) referenceObject;
                                writer.array();
                                for (Object el : o) {
                                    asShortObject(el, json, referencedIdProperty, referencedVersionProperty, referencedDomainClass);
                                }
                                writer.endArray();

                            } else if (referenceObject instanceof Map) {
                                Map<Object, Object> map = (Map<Object, Object>) referenceObject;
                                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                    String key = String.valueOf(entry.getKey());
                                    Object o = entry.getValue();
                                    writer.object();
                                    writer.key(key);
                                    asShortObject(o, json, referencedIdProperty, referencedVersionProperty, referencedDomainClass);
                                    writer.endObject();
                                }
                            }
                        }
                    }
                }
            }
        }
        writer.endObject();
    }


    protected void asShortObject(Object refObj, JSON json, GrailsDomainClassProperty idProperty, GrailsDomainClassProperty versionProperty, GrailsDomainClass referencedDomainClass) throws ConverterException {
        JSONWriter writer = json.getWriter();
        writer.object();
        writer.key("class").value(referencedDomainClass.getFullName());
        writer.key("id").value(extractValue(refObj, idProperty));
        if(isIncludeVersion()) {
            writer.key("version").value(extractValue(refObj, versionProperty));
        }
        writer.endObject();
    }

    protected Object extractValue(Object domainObject, GrailsDomainClassProperty property) {
        BeanWrapper beanWrapper = new BeanWrapperImpl(domainObject);
        return beanWrapper.getPropertyValue(property.getName());
    }

    protected boolean isRenderDomainClassRelations() {
        return false;
    }

    public findUninsertedDependencies = { value ->
        Set dependsOn = new HashSet();
        Set references = new HashSet();

        value = proxyHandler.unwrapIfProxy(value);
        Class clazz = value.getClass();
        GrailsDomainClass domainClass = ConverterUtil.getDomainClass(clazz.getName());
        BeanWrapper beanWrapper = new BeanWrapperImpl(value);

        GrailsDomainClassProperty[] properties = domainClass.getPersistentProperties();

        for (GrailsDomainClassProperty property : properties) {
            if (property.isAssociation()) {
                Object referenceObject = beanWrapper.getPropertyValue(property.getName());
                if (referenceObject != null) {
                    GrailsDomainClass referencedDomainClass = property.getReferencedDomainClass();

                    // Embedded are now always fully rendered
                    if(referencedDomainClass == null || property.isEmbedded() || GrailsClassUtils.isJdk5Enum(property.getType())) {
                        continue;
                    } else if (property.isOneToOne() || property.isManyToOne() || property.isEmbedded()) {
                        //references.add(referenceObject);
                        if (extractValue(referenceObject, referencedDomainClass.getIdentifier()) == null) {
                            dependsOn.add(referenceObject);
                        }
                    } else {
                        GrailsDomainClassProperty referencedIdProperty = referencedDomainClass.getIdentifier();
                        if (referenceObject instanceof Collection) {
                            Collection o = (Collection) referenceObject;
                            for (Object el : o) {
                                //references.add(el);
                                if (extractValue(el, referencedIdProperty) == null) {
                                    dependsOn.add(el);
                                }
                            }
                        } else if (referenceObject instanceof Map) {
                            Map<Object, Object> map = (Map<Object, Object>) referenceObject;
                            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                Object o = entry.getValue();
                                //references.add(o);
                                if (extractValue(o, referencedIdProperty) == null) {
                                    dependsOn.add(o);
                                }
                            }
                        }
                    }
                }
            }
        }

        [ dependsOn, references ]
    }

    private isDirty(obj) {
        def session = AuditTable.sessionFactory.currentSession
        def entry = findEntityEntry(obj, session)
        // Added the || (!entry.loadedState) because .findDirty below would throw a nullpointerexception when .loadedState was null
        if ((!entry) || (!entry.loadedState)) {
            return false
        }

        Object[] values = entry.persister.getPropertyValues(obj, session.entityMode)
        def dirtyProperties = entry.persister.findDirty(values, entry.loadedState, obj, session)
        return dirtyProperties != null
    }


    private static findEntityEntry(instance, session, boolean forDirtyCheck = true) {
        def entry = session.persistenceContext.getEntry(instance)
        if (!entry) {
            return null
        }

        if (forDirtyCheck && !entry.requiresDirtyCheck(instance) && entry.loadedState) {
            return null
        }

        entry
    }

}
