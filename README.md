# usda_dbfront

A Clojure library designed to explore the migration of data from JSON into
Datomic, also includes some very basic querying of the Datomic database.

The database that gets created exists in your computer's memory and doesn't
persist, luckily takes less than two minutes for the migration to occur.

src/*/json_to_datomic.clj handles the actual migration
src/*/dblayer.clj has some samlple queries on the new database
resources/foods-2011-10-03.json is the original json database
resources/datomic-schema.dtm defines the schema for the new database

## Usage

Needs to be done in a REPL at this point, you can step through json_to_datomic.clj of you want, or you can run it by stepping through dblayer.clj

"lein run" does approximately nothing at this point.

## License

Copyright © 2013 Christopher Olsen

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

Also distributed under the GNU GPL version 3 license.  So take your pick.
