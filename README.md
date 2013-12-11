Copyright &copy; 2013 Rafael R. Sevilla
See the end for copying conditions.
***

# BPTDB -- A Simple B+ Tree Flat File Database

The amount of metadata for my game project is growing rapidly, and I
wasted a lot of time looking for a small and simple library that would
suit my needs.  In particular, one requirement was that it be able to
operate with the rather constrained file I/O interface provided by
libgdx, and this proved hardest to satisfy, so I needed to write my
own.  It took longer than it should have, but I probably wasted as
much time looking for someone else's library as I had writing this
one.

BPTDB consists of a Java library for reading these databases and a
Ruby program (ruby/bptree.rb) for building a flat file database based
on a textual form.  It is at present not possible to write databases
in Java: the Ruby program is required for that.

## How to use BPTDB

BPTDB consists of a Java library capable (at the moment) of reading
BPTDB databases, and a Ruby program capable of reading and creating
BPTDB databases, given a data class that describes how data are to be
serialised and deserialised within the database file itself.

### Data Classes

In order to make use of the library, one needs to define what is
called a _data class_.  These data classes are used to serialise and
deserialise objects stored inside the database.

#### Data classes in Ruby

Since Ruby does duck typing, the data class must respond to this
method:

* `decode` -- Given a serialised representation of a database object
  as a binary string, return an instance of the object.

It can additionally respond to the following methods:

* `encode` -- Given an instance of a database object, return a binary
  string that can be decoded by the object.  If this is not defined by
  the data class, the database object itself must then define an
  `encode` method which, if given a string, returns the serialised binary
 representation of the database object.
* `dumpobj` -- Given an instance of a database object, return a
  human-readable representation of the database object.  If this is
  not defined, the `to_s` method on the database object will be used
  instead.
* `parseobj` -- Given a human-readable representation of the object
  produced by `dumpobj`, return the object itself.  Without this, the
  program will decode the string using Beefcake's textual
  representation of Google Protobuf objects.

If `dumpobj` and `parseobj` are not defined, sensible defaults that
are applicable for Protobuf objects with Beefcake are used.  In
particular, `Beefcake::Message` classes and object instances define
all the necessary methods.

#### Data classes in Java

Data classes in Java must conform to the interface `Decoder`, which is
parameterised by `T`, which is the class of database objects inside the
database file.  An instance of such a `Decoder` is provided to the
`BPTDB` instance.   An object conforming to this instance must define
a method called `decode`, which, given an InputStream, reads the
InputStream and returns `null` or an instance of a database object
(class `T`).

A sample Java data class that can be used to decode protobuf objects
is provided in the samples directory.

### The `bptree.rb` tool

The `bptree.rb` tool is used to create and manage databases.  It
requires Ruby 1.9.1 or higher.  To display help, type:

   ruby -I. bptree.rb

The `bptree.rb` tool accepts a database file name on the command line,
and requires one of the `--dump`, `--load`, or `--query` parameters,
to give the mode of operation.

The `--dump` mode dumps the contents of an existing database in
textual form to the file specified, using the `to_s` method of its
data objects, or the `dumpobj` method of the data class provided.  The
output of the dump command produces bracketed key-value pairs.  The
keys are always integers, and the values are the dumps of each of the
input objects.

The `--load` mode reads a text file specified and writes it to the
database file specified.  Each line of text in the file is converted
into a database object and dumped according to the `encode` method of
the object or the data class.  The same format produced by the dump
command can be read by the load command, although one may use strings
as keys in the load command, which are automatically hashed to produce
integer keys for internal use.

The `--query` mode accepts a string as its parameter, and will display
the dumped version of a matching data object if it exists, or nothing
if nothing was found.

The data class may be `require`d by the program using the `--require`
option, and the data class to use is specified by the `--data-class`
parameter.

Basically, one maintains a text file with the data in question, and it
is converted into what is for now a read-only database.

#### Default dump format

The default dump format is based on that used by the Beefcake protobuf
library.

***
Copying and distribution of this file, with or without modification,
are permitted in any medium without royalty provided the copyright
notice and this notice are preserved.
