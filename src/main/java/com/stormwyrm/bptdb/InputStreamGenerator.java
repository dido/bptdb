/*
 * Copyright (c) 2013 Rafael R. Sevilla (http://games.stormwyrm.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.stormwyrm.bptdb;

import java.io.InputStream;

/**
 * An InputStreamGenerator simply returns an java.io.InputStream object somehow.  This is
 * an abstraction of a libgdx FileHandle, and a simple implementation of this interface
 * would simply wrap such a FileHandle.  It could also be used to wrap other types of
 * InputStream classes.
 */
public interface InputStreamGenerator
{
	/**
	 * Return a java.io.InputStream representing a database file.  This stream must be
	 * pointing to the beginning of the file whenever this method is called. 
	 */
	InputStream read();
}
