/**
 * Primitive Collection Framework for Java
 * Copyright (C) 2010 Napile.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.github.joealisson.primitive;

/**
 * @author VISTALL
 *
 * This is interface contains the same methods of #Collection and ##Map
 */
public interface Container
{
	/**
	 * @return the number of elements in this container
	 */
	int size();

	/**
	 * Removes all of the elements from this container
	 */
	void clear();

	/**
	 * @return true if container if size is equal zero
	 */
	boolean isEmpty();
}
