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
  string representation of the database object.
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
particular, Beefcake::Message classes and object instances define all
the necessary methods.

#### Data classes in Java

Data classes in Java must conform to the interface `Decoder`, which is
parameterised by `T`, which is the class of database objects inside the
database file.  An instance of such a `Decoder` is provided to the
`BPTDB` instance.   An object conforming to this instance must define
a method called `decode`, which, given an InputStream, reads the
InputStream and returns `null` or an instance of a database object
(class `T`).

### Building a Database

The `bptree.rb` tool is used to create and manage databases.