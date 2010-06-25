package library

class JBook {
    static audit = true
    
    String title
    Integer edition
    List authors
    
    static hasMany = [ authors : JAuthor ]
    
    static constraints = {
        title(blank:false)
    }

    String toString() {
        "${id} ${title} ed.${edition} By ${authors}"
    }
}
