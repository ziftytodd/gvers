package library

import grails.test.*
import com.supersites.AuditTable

class IntegrationBookTestsTests extends GrailsUnitTestCase {
    def sessionFactory
    
    protected void setUp() {
        super.setUp()
    }

    protected void tearDown() {
        super.tearDown()
    }

    void testJSON() {
        def book = new JBook(title:"Common Sense", edition:1)
        def author1 = new JAuthor(firstName:"Thomas", lastName:"Payne")
        def author2 = new JAuthor(firstName:"George", lastName:"Washington", inner:new InnerClass(data:"Some data"))
        book.addToAuthors(author1)
        book.addToAuthors(author2)
        assert book.save(flush:true)

        AuditTable.list().each {
            println it
        }
        
        def ab = JBook.get(book.id, 0)
        println " *** Initial ab=${ab}"

        sessionFactory.currentSession.clear()
        author2 = JAuthor.get(author2.id)
        author2.firstName = "Georgey"
        author2.save(flush:true)
        ab = JBook.getLatest(book.id)
        println " *** Changed George's first name ab=${ab}"
        AuditTable.list().each {
            println it
        }

        sessionFactory.currentSession.clear()
        book = JBook.get(book.id)
        book.title = "More Common Sense"
        book.addToAuthors(new JAuthor(firstName:"Todd", lastName:"Miller"))
        def tmp = book.authors[0]
        book.authors[0] = book.authors[2]
        book.authors[2] = tmp
        book.save(flush:true)
        ab = JBook.getLatest(book.id)
        println " *** Changed book title ab=${ab}"
        AuditTable.list().each {
            println it
        }

        sessionFactory.currentSession.clear()
        book = JBook.get(book.id)
        author1 = JAuthor.get(author1.id)
        book.removeFromAuthors(author1)
        author1.delete(flush:true)
        //book.save(flush:true)
        ab = JBook.getLatest(book.id)
        println " *** Removed Thomas Payne ab=${ab}"
        AuditTable.list().each {
            println it
        }

        sessionFactory.currentSession.clear()
        def mag = new JMagazine(title:"Cuisine at Home", headline:"30 minute meals", edition:1, rating:MovieRating.PG13)
        mag.addToAuthors(new JAuthor(firstName:"Emily", lastName:"Dickinson"))
        mag.save(flush:true)
        def magHistory = JBook.getLatest(mag.id)
        println " *** Magazine inserted mag=${magHistory}"
        AuditTable.list().each {
            println it
        }
        
    }
}
