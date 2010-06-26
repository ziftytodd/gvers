import com.supersites.AuditTable

import java.lang.reflect.Modifier;

class GversGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.2 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "test/**"
    ]

    def observe = ['*']

    // TODO Fill in these fields
    def author = "Todd Miller"
    def authorEmail = "todd@supersites.com"
    def title = "Domain auditing similar to envers, implemented totally via GORM/grails"
    def description = '''\\
This plug-in will create a table and store changes (inserts, updates, and deletes) made to any
domain object in your project that has \"static audit = true\" defined. It supports simple data
types, collections, enums, and one-level of inheritence. Regardless of how many classes are
audited, all audit data is stored in a single table. Use your objects as normal, using .save() or .delete().
The plug-in adds two additional methods to the audited classes, .get(id, version) to retrieve
a specific version of an object, and .getLatest(id) to get the latest version stored in the audit table
of an object. The version number of an object corresponds to the version field added to all domain objects
by GORM for optimistic locking.

Audit entries store the time that the change was made, as well as what type of change it was (insert,
update, or delete).
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/gvers"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
    }

    def doWithDynamicMethods = { ctx ->
		application.domainClasses.each { domainClass ->
			if (getStaticProperty(domainClass, 'audit')) {
				domainClass.clazz.metaClass.static.get = { id, version ->
					AuditTable.get(domainClass.clazz, id, version)
			    }

				domainClass.clazz.metaClass.static.getLatest = { id ->
					AuditTable.getLatest(domainClass.clazz, id)
                }

                domainClass.clazz.metaClass.afterInsert = {
                    AuditTable.saveAudit(delegate, AuditTable.INSERT)
                }

                domainClass.clazz.metaClass.afterUpdate = {
                    AuditTable.saveAudit(delegate, AuditTable.MODIFY)
                }

                domainClass.clazz.metaClass.afterDelete = {
                    AuditTable.saveAudit(delegate, AuditTable.DELETE)
                }
            }
        }

        AuditTable.grailsApplication = application
    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        doWithDynamicMethods(event.ctx)
    }

    def onConfigChange = { event ->
        doWithDynamicMethods(event.ctx)
    }

    def getStaticProperty(def domainClass, String name) {
		Object value = null;
		MetaProperty metaProperty = domainClass.clazz.metaClass.getMetaProperty(name)
		if(metaProperty != null) {
			int modifiers = metaProperty.getModifiers();
			if(Modifier.isStatic(modifiers)) {
				value = metaProperty.getProperty(domainClass.clazz);
			}
		}

        value
	}

}
