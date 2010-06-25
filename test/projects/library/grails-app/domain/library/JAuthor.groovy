package library

class JAuthor {
    static audit = true

    String firstName
    String lastName
    InnerClass inner

    static embedded = [ 'inner' ]

    static belongsTo = [ book : JBook ]

    static constraints = {
        inner(nullable:true)
    }

    String toString() { "${lastName}, ${firstName} wrote ${book.title} ed.${book.edition} inner=${inner?.data}"}
}

class InnerClass {
    String data
}