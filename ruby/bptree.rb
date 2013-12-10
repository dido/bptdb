#!/usr/bin/env ruby
# -*- coding: utf-8 -*-
#
# Copyright (c) 2013 Rafael R. Sevilla (http://games.stormwyrm.com)
#
#
# Licensed under the Apache License, Version 2.0 (the "License"); you
# may not use this file except in compliance with the License. You may
# obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied. See the License for the specific language governing
# permissions and limitations under the License.
#
# --------------------------------------------------------------------
#
# Basic file structure of our B+ tree database is as follows:
#
# Each page is 256 bytes in size, and can contain up to 64 32-bit integer
# values.
#
# Keys can be any 32-bit signed integer with the exception of INFIMUM_KEY,
# which is the minimum negative 32-bit two's complement signed integer.
#
# Pages can be of three types:
# 1. Data page
# 2. Internal page
# 3. Leaf page
#
# Data pages contain actual value information, and the interpretation of
# the remaining 256 bytes inside it is up to the application.  There is
# no marker that denotes a data page: a page is a data page if and only
# if it is pointed to by a leaf node.
#
# All pages contain a type descriptor (1 bit) plus a count of valid
# keys (31 bits), and a parent pointer (32 bits), leaving 248 bytes / 62
# values available to use for other purposes.
#
# Leaf pages can contain up to 31 keys and 31 file offsets to data pages,
# taking up a total of 240 bytes. The keys are always sorted in
# ascending order.
#
# Internal pages contain:
# 1. An infimum link (8 bytes), which is given a "key" with a value of
#    INFIMUM_KEY and a file offset to another page associated with all
#    data less than any other key explicitly present.
# 2. Up to 30 keys and associated file offsets to other pages that are
#    associated with data greater than or equal to the key, but less than
#    the following key. (240 bytes)
# As with leaf pages, the keys are always sorted in order.
#
# The type descriptor is 32 bits, and the low bit tells us if it's an
# internal page or a leaf page. The rest of the bits tell us how many
# entries are used, up to 30 (counting the infimum key for internal pages).
#
# NOTE: Deleting data is at present impossible to do directly.  The only
# way to do it as of now is to dump all the data and then restore, with
# the deleted data removed.
#
VERSION = "0.0.1"

Encoding.default_internal = "utf-8"

# 32-bit MurmurHash
class MHash
  def self.rotl32(x, r)
    return(((x << r) | (x >> (32 - r))) & 0xffffffff)
  end

  def self.fmix32(h)
    h ^= h >> 16
    h *= 0x85ebca6b
    h &= 0xffffffff
    h ^= h >> 13
    h *= 0xc2b2ae35
    h &= 0xffffffff
    h ^= h >> 16
    return(h)
  end

  def self.hash(key, seed=0xdeadbeef)
    c1 = 0xcc9e2d51
    c2 = 0x1b873593
    k1 = 0
    h1 = seed
    index = 0
    len = 0
    if key.class == String
      key = key.bytes
    end
    key.each do |val|
      k1 |= val << (index * 8)
      index = (index + 1) % 4
      len += 1
      unless index == 0
        next
      end
      k1 *= c1
      k1 &= 0xffffffff
      k1 = rotl32(k1, 15)
      k1 *= c2
      k1 &= 0xffffffff
      
      h1 ^= k1
      h1 = rotl32(h1,13)
      h1 = h1*5 + 0xe6546b64
      h1 &= 0xffffffff
      k1 = 0
    end
    if index != 0
      # tail
      k1 *= c1
      k1 &= 0xffffffff
      k1 = rotl32(k1, 15)
      k1 *= c2
      k1 &= 0xffffffff
      h1 ^= k1
    end
    h1 ^= len
    h1 = fmix32(h1)
    if h1 >= (1 << 31)
      h1 -= (1 << 32)
    end
    return(h1)
  end
end

# Dummy key for infimum
INFIMUM_KEY = -2147483648
# page size
PAGESIZE = 256
# data element size (default 4 bytes, 32 bits)
DATASIZE = 4
# Maximum number of key/value pairs per page
MAXKEYS = (PAGESIZE/(DATASIZE*2)) - 2

# Flags for page types
IPAGE = 0
LPAGE = 1

class FullPageException < RuntimeError
end

class Page
  attr_accessor :offset, :parent

  def initialize
    @data = []
    @offset = nil
    @parent = 0
  end

  private
  # Find the nearest keys. Returns the index of the nearest key less than k,
  # and the nearest key greater than k.  If the key is already present,
  # returns the index of the key itself.
  def find(k)
    first = 0
    last = @data.length - 1
    if @data[first].nil?
      return(nil)
    end

    if @data[first][0] > k
      return([nil, 0])
    end

    if @data[last][0] < k
      return([last, nil])
    end

    while last - first > 1
      mid = (first + last) / 2
      if @data[mid][0] == k
        return([mid, mid])
      end
      if @data[mid][0] < k
        first = mid
        next
      end
      last = mid
    end

    if @data[first][0] == k
      last = first
    elsif @data[last][0] == k
      first = last
    end

    return([first, last])
  end

  public

  # Add a new key-value mapping.  Keep the assoc array sorted.
  def add(k, v)
    if @data.length >= MAXKEYS
      raise FullPageException, "Page full"
    end

    lt, gt = find(k)

    # If it is at the end of the array, just add it to the end
    if gt.nil?
      @data << [k, v]
      return(v)
    end

    # replace the key if already there
    if lt == gt
      @data[lt] = [k, v]
      return(v)
    end

    @data.insert(gt, [k, v])
    return(v)
  end

  def write(fp)
    fp.seek(@offset, IO::SEEK_SET)
    ofs = fp.tell
    desc = (self.class == LPage) ? LPAGE : ((self.class == IPage) ? IPAGE : (raise "can't write bare Page"))
    fp.write([desc | (@data.length << 1), @parent].pack("l>2"))
    count = 0   
    @data.sort! { |x, y| x[0] <=> y[0] }
    @data.each do |val|
      fp.write(val.pack("l>*"))
      count += 1
    end
    count.upto(MAXKEYS) { fp.write([0,0].pack("l>*")) }
  end

  def each
    @data.each do |val|
      yield val
    end
  end

  def self.load(fp)
    offset = fp.tell
    t = fp.read(4).unpack("l>").first
    len = t >> 1
    c = (t & 0x1 == IPAGE) ? IPage :  LPage
    p = c.new
    p.offset = offset
    p.parent = fp.read(4).unpack("l>").first
    len.times do
      k, v = fp.read(8).unpack("l>2")
      p.add(k, v)
    end
    return(p)
  end
end

class LPage < Page
  # Get the mapping of k if it exists.  This returns only the exact value.
  # Returns nil if the mapping doesn't exist.
  def get(k)
    lt, gt = find(k)
    if lt.nil? || lt != gt
      return(nil)
    end

    return(@data[lt][1])
  end

  # Get the smallest key
  def inf
    return(@data.first.first)
  end

  def split(npage)
    mid = @data.length / 2
    halfkeys = @data.slice!(mid..-1)
    halfkeys.each do |k,v|
      npage.add(k,v)
    end
    return(npage.inf)
  end
end

class IPage < Page
  def inf=(ofs)
    add(INFIMUM_KEY, ofs)
  end

  ##
  # Given a key k, find the key less than or equal to k.
  def get(k)
    lo, = find(k)
    return(@data[lo][1])
  end

  ##
  # Split an internal page.  This works slighly differently from splitting
  # leaf pages. We again split the original page in half, but we remove the
  # first key/value pair.  The first value becomes the new page's infimum,
  # and the key is returned.  It should be inserted to the parent, with the
  # new page as value.
  def split(npage)
    mid = @data.length / 2
    halfkeys = @data.slice!(mid..-1)
    mk, mv = halfkeys.shift
    npage.inf = mv
    halfkeys.each do |k,v|
      npage.add(k,v)
    end
    return(mk)
  end
end

class BPTDB
  def initialize(filename, dataclass)
    @filename = filename
    @dataclass = dataclass
  end

  private

  # Save a page to disk. If the offset is nil, this will seek to the end
  # of the index file and save it there, updating the offset field as
  # appropriate
  def savepage(page)
    File.open(@filename, "r+b:ascii-8bit") do |fp|
      # If page has no recorded offset, add it to the end of the
      # file.
      if page.offset.nil?
        fp.seek(0, IO::SEEK_END)
        page.offset = fp.tell
      end
      fp.seek(page.offset, IO::SEEK_SET)
      page.write(fp)
    end
  end

  def loadpage(offset)
    File.open(@filename, "rb:ascii-8bit") do |fp|
      fp.seek(offset, IO::SEEK_SET)
      return(Page.load(fp))
    end
  end

  def savedata(data, offset=nil)
    if data.class == Fixnum
      return(data)
    end
    File.open(@filename, "r+b:ascii-8bit") do |fp|
      if offset.nil?
        fp.seek(0, IO::SEEK_END)
        offset = fp.tell
      end
      fp.seek(offset)
      bdata = data.encode("")
      len = bdata.length
      if len > PAGESIZE
        raise "data too big to fit in page"
      end
      fp.write([len].pack("C"))
      fp.write(bdata)
      # bdata = data.encode("")
      # # Add padding to the data if needed
      # padlen = PAGESIZE - bdata.length
      # if padlen <= 0
      #   raise "data too big to fit in page"
      # end
      # padchr = padlen.chr
      # bdata << padchr * padlen
      # fp.write(bdata)
      return(offset)
    end
  end

  def loaddata(offset)
    File.open(@filename, "rb:ascii-8bit") do |fp|
      fp.seek(offset, IO::SEEK_SET)
      len, = fp.read(1).unpack("C")
      data = fp.read(len)
      # bdata = fp.read(PAGESIZE)
      # padlen = bdata[-1].codepoints.first
      # data = bdata[0, PAGESIZE-padlen]
      return(@dataclass.decode(data))
    end
  end

  # Split a page, and add the key/value pair to the appropriate new page
  def splitpage(page, k, v)
    loop do
      # Split the page. We do the same thing in both cases.
      # First, create a new page of the same type
      npage = page.class.new 
      npage.parent = page.parent
      # Split the original page into the new page
      nk = page.split(npage)
      # Add the key/value pair to the new page if it is greater than
      # the midpoint key at the split, to the old page otherwise.
      if k > nk
        npage.add(k, v)
      else
        page.add(k, v)
      end
      # Write back the pages
      savepage(page)
      savepage(npage)
      # If the root had split, what happens is that we have to create a new
      # root, which becomes the parent of the two pages.
      ppage = nil
      if page.offset == 0
        page.offset = nil
        # copy the old page information to a new location
        savepage(page)
        # Make a new IPage that will become the new root
        ppage = IPage.new
        ppage.offset = 0
        ppage.parent = 0
        ppage.inf = page.offset
      else
        # Load the parent, and then add the midpoint key, pointing to the
        # newly-created page.
        ppage = loadpage(npage.parent)
      end
      begin
        ppage.add(nk, npage.offset)
        savepage(ppage)
        break
      rescue FullPageException
        # If the page was full, do the same thing for the parent page,
        # adding the new key and page offset (value)
        page = ppage
        k = nk
        v = npage.offset
      end
    end
    return(nil)
  end

  # Return the leaf page where the key might be found.
  def search(key)
    offset = 0
    loop do
      begin
        page = loadpage(offset)
      rescue
        return(nil)
      end
      if page.class == LPage
        return(page)
      end
      offset = page.get(key)
    end
  end

  # Parse an object description
  def parseobj(text)
    if @dataclass.respond_to?(:parseobj)
      return(@dataclass.parseobj(text))
    end
    unless /^<([A-Za-z]+)\s+(.+)/ =~ text
      raise "Invalid object format"
    end
    clsname = $1
    fields = $2
    cls = clsname.split('::').inject(Object) { |o, c| o.const_get(c) }
    val = cls.new
    name = value = nil
    until fields[0] == ">"
      # numbers XXX - cannot yet parse floats
      if /^([A-Za-z]+):\s+(-?[0-9]+)(.*)$/ =~ fields
        name = $1
        value = $2.to_i
        fields = $3
        # bools
      elsif /^([A-Za-z]+):\s+(true|false)(.*)$/ =~ fields
        name = $1
        value = ($2 == "true") ? true : false
        fields = $3
      elsif /^([A-Za-z]+):\s+(\".+\")(.*)$/ =~ fields
        # strings
        name = $1
        # XXX: I wonder if there's a cleaner way than this
        value = eval($2)
        fields = $3
      elsif /^([A-Za-z]+):\s+(<.+)$/ =~ fields
        # other objects
        name = $1
        value, fields = parseobj($2)
      else
        raise "parse error"
      end
      val.send((name + "=").intern, value)
      if /^,\s+(.*)/ =~ fields
        fields = $1
      end
    end
    fields = fields[1..-1]
    return([val, fields])
  end

  def fixnumkey(key)
    unless key.class == Fixnum
      key = MHash.hash(key)
    end
    return(key)
  end

  public

  def add(key, value)
    key = fixnumkey(key)
    # search always returns a leaf page or nil when the file does not exist
    page = search(key)
    if page.nil?
      # create a new empty root page
      page = LPage.new
      page.offset = 0
      File.open(@filename, "w") { }
      savepage(page)
    end

    # Look for the key in our database.  If it is already present,
    # we just have to overwrite the data page already created.
    ofs = page.get(key)
    newpage = ofs.nil?
    ofs = savedata(value, ofs)
    unless newpage
      # No new page was created, no re-indexing required, we are done.
      return(value)
    end

    # If a new data page was created, we have to add in the page's
    # offset to the tree.
    begin
      page.add(key, ofs)
      savepage(page)
      return(value)
    rescue FullPageException
    end

    # The page seems to be full.  Split the page, which will take care of
    # adding them to the correct destinations.
    splitpage(page, key, ofs)
    return(value)
  end

  def getofs(key)
    key = fixnumkey(key)
    page = search(key)
    if page.nil?
      return(nil)
    end
    offset = page.get(key)
    if offset.nil?
      return(nil)
    end
    return(offset)
  end

  def get(key)
    return(loaddata(getofs(key)))
  end

  # Get all the leaves and print in human-readable form.  Note key names
  # if they were initially specified in text are gone
  def dump(fp, page=nil)
    if page.nil?
      # start with the root
      page = loadpage(0)
    end
    if page.class == LPage
      page.each do |k,v|
        v = loaddata(v)
        fp.puts([k, v].inspect)
      end
      return
    end
    page.each do |k,offset|
      npage = loadpage(offset)
      dump(npage)
    end
  end

  # Load a dump of a database
  def load(fp)
    count = 0
    fp.each_line do |line|
      count += 1
      unless /^\[([0-9A-Za-z_\-]+),\s+(.+)\]/ =~ line
        raise "line has invalid format"
      end
      key = $1
      val, = parseobj($2)
      if count == 63
        @mykey = key
      end
      add(key, val)
      unless @mykey.nil?
        get(@mykey)
      end
    end
  end
end

if __FILE__ == $0
  require 'trollop'

  opts = Trollop::options do
    version "BPTree Database #{VERSION} (c) 2013 Stormwyrm"
    banner <<-EOS
BPTree

Usage:
  bptree.rb [options] file
where [options] are:
EOS
    opt :dump, "Dump a database to text", :type => :string
    opt :load, "Load a database from text", :type => :string
    opt :query, "Query a database key", :type => :string
    opt :require, "Require data classes", :type => :string, :multi => true
    opt :data_class, "Data class of objects in the database", :type => :string
  end

  dbfile = ARGV[0]
  if dbfile.nil? || dbfile.empty?
    Trollop::die "Database file not specified"
  end

  opts[:require].each do |rf|
    reqfile = File.absolute_path(rf)
    unless File.exist?(reqfile)
      Trollop::die :require, "specifies file #{rf} that does not exist"
    end
    require reqfile
  end

  if opts[:data_class].nil? || opts[:data_class].empty?
    Trollop::die :data_class, "must be specified"
  end

  begin
    dataclass = opts[:data_class].split('::').inject(Object) { |o, c| o.const_get(c) }
  rescue
    Trollop::die :data_class, "#{opts[:data_class]} is not defined"
  end

  if dataclass.class != Class
    Trollop::die :data_class, "#{opts[:data_class]} does not specify a class"
  end

  db = BPTDB.new(dbfile, dataclass)

  mode = nil
  arg = nil
  if opts[:dump_given]
    mode = :dump
    arg = opts[:dump]
  elsif opts[:load_given]
    mode = :load
    arg = opts[:load]
  elsif opts[:query_given]
    mode = :query
    arg = opts[:query]
  end

  if mode == :dump
    if arg == "-"
      db.dump(STDOUT)
    else
      File.open(arg, "w") { |fp| db.dump(fp) }
    end
  elsif mode == :load
    if arg == "-"
      db.load(STDIN)
    else
      File.open(arg, "r") { |fp| db.load(fp) }
    end
  elsif mode == :query
    puts db.get(arg).inspect
  else
    Trollop::die "one of --dump, --load, or --query must be specified"
  end
end
