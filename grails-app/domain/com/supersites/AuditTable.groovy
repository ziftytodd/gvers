package com.supersites

import grails.converters.*

import java.lang.reflect.Modifier;

//import java.util.regex.Matcher
//import org.hibernate.FlushMode

import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.web.converters.ConverterUtil;
import org.codehaus.groovy.grails.web.json.*;

class AuditTable implements Serializable {
    static def grailsApplication

    public static final int INSERT = 0
    public static final int MODIFY = 1
    public static final int DELETE = 2

    String objectClass
    Long objectId
    Long objectVersion
    String json
    Integer event
    Date dateCreated

    static constraints = {
    }

    static mapping = {
		version false
		id composite:['objectClass', 'objectId', 'objectVersion'], generator:'assigned'
        json type:'text'
    }

    String toString() { "${objectClass} ${objectId}-${objectVersion} ${event} ${dateCreated} json=${json}" }

    def beforeUpdate = { false }
    def beforeDelete = { false }

    static Object get(c, Long i, Long v) {
        AuditTable.get(c, i, v, [:])
    }

    public static Object get(c, Long i, Long v, Map unmarshalling) {
        def cn = AuditTable.getSuperClassName(c)

        if (unmarshalling["${cn}-${i}-${v}"]) {
            unmarshalling["${cn}-${i}-${v}"]
        } else {
            def results = AuditTable.withCriteria {
                and {
                    eq('objectClass', cn)
                    eq('objectId', i)
                    eq('objectVersion', v)
                }
                maxResults(1)
                setCacheable(true)
            }

            def result = results ? results[0] : null
            if (result) {
                // Unmarshal the object
                AuditTable.unmarshal(result, unmarshalling)
            } else {
                null
            }
        }
    }

    public static getLatest = { c, Long i ->
        def cn = AuditTable.getSuperClassName(c)
        def results = AuditTable.withCriteria {
            and {
                eq('objectClass', cn)
                eq('objectId', i)
            }
            order('objectVersion','desc')
            maxResults(1)
            setCacheable(false)
        }

        def result = results ? results[0] : null
        if (result) {
            // Unmarshal the object
            AuditTable.unmarshal(result, [:])
        } else {
            null
        }
    }

    public static loadEntry = { obj ->
        def cn = AuditTable.getSuperClassName(obj.class)
        if (cn.indexOf('_$$_javassist_') > 0) {
            cn = cn.substring(0, cn.indexOf('_$$_javassist_'))
        }
        //println "loadingEntry ${cn} ${obj.id} ${obj.version}"
        def results = AuditTable.withCriteria {
            and {
                eq('objectClass', cn)
                eq('objectId', obj.id)
                eq('objectVersion', obj.version)
            }
            maxResults(1)
            setCacheable(false)
        }

        results ? results[0] : null
    }

    public static saveAudit = { obj, event ->
        def dcm = new MyDomainClassMarshaller(true);
        def relations = (event != AuditTable.DELETE) ? dcm.findUninsertedDependencies(obj) : [ [], [] ];
        def dependsOn = relations[0]
        //def references = relations[1]

        if (dependsOn) {
            // Can't insert audit entry yet due to uninserted collection elements
            AuditTable.makePending(obj, dependsOn, event)
        } else {
            JSON.createNamedConfig("with-version") {
                it.registerObjectMarshaller(dcm)
            }

            JSON.use("with-version") {
                def auditEntry
                if (event == AuditTable.DELETE) {
                    auditEntry = new AuditTable(objectClass:AuditTable.getSuperClassName(obj.class), objectId:obj.id, objectVersion:(obj.version+1), json:"null", event:event)
                } else {
                    def converter = obj as JSON
                    def s = converter.toString()
                    auditEntry = new AuditTable(objectClass:AuditTable.getSuperClassName(obj.class), objectId:obj.id, objectVersion:obj.version, json:s, event:event)
                }

                AuditTable.withNewSession { org.hibernate.Session session ->
//                    COMMENTED OUT UPDATING REFERENCES IN OTHER AUDIT ENTRIES TO POINT TO UPDATED ENTRY FOR THIS OBJECT.
//                    HANDLING COLLECTIONS IS TRICKY BECAUSE THE ONLY WAY TO REALLY HAVE TRUE CONSISTENCY IS TO VERSION
//                    ANY OBJECT THAT THIS ONE TOUCHES (AND RECURSIVELY ANY THAT THEY TOUCH), WHICH IN A COMPLEX AND HIGHLY
//                    CONNECTED DATABASE SCHEMA, IT COULD BASICALLY RESULT IN A SNAPSHOT OF A LARGE PORTION OF THE DATABASE ALL
//                    BECAUSE OF A TRIVIAL UPDATE MADE TO ONE ROW IN ONE TABLE.

//                    def updateRefs = []
//                    FlushMode flushMode = session.flushMode
//                    session.flushMode = FlushMode.MANUAL
//                    try {
//                        def find = "\\{\"class\":\"${obj.class.name}\",\"id\":${obj.id},\"version\":(\\d*)\\}"
//                        def updated = "\\{\"class\":\"${obj.class.name}\",\"id\":${obj.id},\"version\":${obj.version}\\}"
//                        references.each {
//                            def refAuditEntry = AuditTable.loadEntry(it)
//                            if (refAuditEntry) {
//                                Matcher m = refAuditEntry.json =~ /${find}/
//                                if (m) {
//                                    refAuditEntry.json = refAuditEntry.json.replaceAll(/${find}/, updated)
//                                    updateRefs.add(refAuditEntry)
//                                }
//                            }
//                        }
//                    } finally {
//                        session.flushMode = flushMode
//                    }

                    session.save(auditEntry)
//                    updateRefs.each { session.save(it) }
                }
            }

            // Handle pending in case any objects are dependent upon us
            AuditTable.handlePending(obj)
        }
    }

    static pendingObjects = [:]
    static dependentObjects = [:]

    static handlePending = { dependent ->
        if (dependentObjects[dependent]) {
            dependentObjects[dependent].each { pending ->
                def pendingOn = pendingObjects[pending]
                pendingOn[0].remove(dependent)
                if (pendingOn[0].size() == 0) {
                    // Ready to finally insert!
                    pendingObjects.remove(pending)
                    AuditTable.saveAudit(pending, pendingOn[1])
                    //println "Finally saved ${pending}.. pendingObjects=${pendingObjects}"
                }
            }

            // Now that we're saved, nothing is depending on us so remove totally from dependent objects set
            dependentObjects.remove(dependent)
        }
    }

    static makePending = { pending, Set dependsOn, event ->
        //println "${pending} depends on ${dependsOn}"

        if (pendingObjects[pending]) {
            pendingObjects[pending][0].addAll(dependsOn)
        } else {
            pendingObjects[pending] = [ dependsOn, event ]
        }

        dependsOn.each { dependent ->
            if (dependentObjects[dependent]) {
                dependentObjects[dependent].add(pending)
            } else {
                dependentObjects[dependent] = [ pending ]
            }
        }
    }

    static unmarshal = { entry, unmarshalling ->
        def m = JSON.parse(entry.json)
        if ((m == JSONObject.NULL) || (m['class'] == null)) return null
        Class clazz = grailsApplication.classLoader.loadClass(m['class'])
        GrailsDomainClass domainClass = ConverterUtil.getDomainClass(clazz.name);

        // Create an instance of the class
        def result = clazz.newInstance()

        // Set id and version fields
        result.id = entry.objectId
        result.version = entry.objectVersion

        // Put into unmarshalling map to avoid infinite loops when dereferencing collection elements
        unmarshalling["${entry.objectClass}-${entry.objectId}-${entry.objectVersion}"] = result

        AuditTable.unmarshalInstance(result, m, domainClass.persistentProperties, unmarshalling)
    }

    static unmarshalInstance = { result, m, persistentProperties, unmarshalling ->
        persistentProperties.each { property ->
            if (m[property.name]) {
                if (property.isEnum()) {
                    def embeddedMap = m[property.name]
                    Class embeddedClazz = grailsApplication.classLoader.loadClass(embeddedMap['enumType'])
                    result."${property.name}" = embeddedClazz."${embeddedMap['name']}"
                } else if (!property.isAssociation()) {
                    // Restore non-relation property
                    if (m[property.name]) {
                        result."${property.name}" = m[property.name]
                    }
                } else {
                    Object referenceObject = m[property.name]
                    if ((referenceObject == null) || (referenceObject == JSONObject.NULL)) {
                        result."${property.name}" = null
                    } else {
                        if(property.isEmbedded()) {
                            // Handle embedded class
                            def embeddedMap = m[property.name]
                            Class embeddedClazz = grailsApplication.classLoader.loadClass(embeddedMap['class'])
                            def embedded = embeddedClazz.newInstance()
                            result."${property.name}" = AuditTable.unmarshalInstance(embedded, embeddedMap, property.getComponent().getPersistentProperties(), unmarshalling)
                        } else if (m[property.name] instanceof JSONArray) {
                            // Handle loading audited versions of elements in a collection
                            m[property.name].each { subEntry ->
                                def loadedEntry = AuditTable.get(subEntry['class'], subEntry['id'] as Long, subEntry['version'] as Long, unmarshalling)
                                result."addTo${property.name.capitalize()}"(loadedEntry)
                            }
                        } else {
                            // Not an array, so just unmarshal?
                            def subEntry = m[property.name]
                            def loadedEntry = AuditTable.get(subEntry['class'], subEntry['id'] as Long, subEntry['version'] as Long, unmarshalling)
                            result."${property.name}" = loadedEntry
                        }
                    }
                }
            }
        }

        result
    }

    static getSuperClassName = { clazz ->
        if (clazz instanceof Class) {
            if(!clazz.getSuperclass().equals( GroovyObject.class ) &&
               !clazz.getSuperclass().equals(Object.class) &&
               !Modifier.isAbstract(clazz.getSuperclass().getModifiers())) {
                clazz.getSuperclass().name
            } else {
                clazz.name
            }
        } else {
            clazz
        }
    }
}
