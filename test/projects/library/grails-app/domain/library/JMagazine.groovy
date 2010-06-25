package library

class JMagazine extends JBook {
    static audit = true

    String headline
    MovieRating rating = MovieRating.UNRATED

    static constraints = {
    }

    String toString() { "Mag${id} ${title} issue ${edition} \"${headline}\" ${rating}" }
}


public enum MovieRating {
    UNRATED('Unrated'),
    G('G'),
    PG('PG'),
    PG13('PG-13'),
    R('R'),
    NC17('NC-17')

	final String id

	MovieRating(String id) {
		this.id = id
	}

	String toString() { id }

	String getKey() { name() }

	static list() {
		[UNRATED, G, PG, PG13, R, NC17]
	}
}
