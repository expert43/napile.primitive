/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package io.github.joealisson.primitive.sets.impl;

import io.github.joealisson.primitive.sets.IntSet;
import io.github.joealisson.primitive.pair.IntObjectPair;
import io.github.joealisson.primitive.collections.IntCollection;
import io.github.joealisson.primitive.comparators.IntComparator;
import io.github.joealisson.primitive.iterators.IntIterator;
import io.github.joealisson.primitive.maps.NavigableIntObjectMap;
import io.github.joealisson.primitive.maps.impl.TreeIntObjectMap;
import io.github.joealisson.primitive.sets.NavigableIntSet;
import io.github.joealisson.primitive.sets.SortedIntSet;
import io.github.joealisson.primitive.sets.abstracts.AbstractIntSet;

/**
 * <p>
 * A {@link NavigableIntSet} implementation based on a {@link TreeIntObjectMap}.
 * The elements are ordered using their {@linkplain Comparable natural
 * ordering}, or by a {@link IntComparator} provided at set creation
 * time, depending on which constructor is used.
 * </p>
 * <p>This implementation provides guaranteed log(n) time cost for the basic
 * operations ({@code add}, {@code remove} and {@code contains}).
 * </p>
 * <p>Note that the ordering maintained by a set (whether or not an explicit
 * comparator is provided) must be <i>consistent with equals</i> if it is to
 * correctly implement the {@code Set} interface.  (See {@code Comparable}
 * or {@code Comparator} for a precise definition of <i>consistent with
 * equals</i>.)  This is so because the {@code Set} interface is defined in
 * terms of the {@code equals} operation, but a {@code TreeSet} instance
 * performs all element comparisons using its {@code compareTo} (or
 * {@code compare}) method, so two elements that are deemed equal by this method
 * are, from the standpoint of the set, equal.  The behavior of a set
 * <i>is</i> well-defined even if its ordering is inconsistent with equals; it
 * just fails to obey the general contract of the {@code Set} interface.
 * </p>
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a tree set concurrently, and at least one
 * of the threads modifies the set, it <i>must</i> be synchronized
 * externally.  This is typically accomplished by synchronizing on some
 * object that naturally encapsulates the set.
 * If no such object exists, the set should be "wrapped" using the
 * {@link java.util.Collections#synchronizedSortedSet Collections.synchronizedSortedSet}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the set: <pre>
 *   SortedSet s = Collections.synchronizedSortedSet(new TreeSet(...));</pre>
 *
 * <p>The iterators returned by this class's {@code iterator} method are
 * <i>fail-fast</i>: if the set is modified at any time after the iterator is
 * created, in any way except through the iterator's own {@code remove}
 * method, the iterator will throw a {@link java.util.ConcurrentModificationException}.
 * Thus, in the face of concurrent modification, the iterator fails quickly
 * and cleanly, rather than risking arbitrary, non-deterministic behavior at
 * an undetermined time in the future.
 * </p>
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 * </p>
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author Josh Bloch
 * @version %I%, %G%
 * @see	 IntCollection
 * @see     IntSet
 * @see	 HashIntSet
 * @see	 TreeIntObjectMap
 * @see Comparable
 * @see IntComparator
 * @since 1.0.0
 */

public class TreeIntSet extends AbstractIntSet implements NavigableIntSet, Cloneable, java.io.Serializable
{

    public static final long serialVersionUID = -3669731982236554521L;

    /**
     * The backing map.
     */
    private transient NavigableIntObjectMap<Object> m;

    // Dummy value to associate with an Object in the backing Map
    private static final Object PRESENT = new Object();

    /**
     * Constructs a set backed by the specified navigable map.
     *
     * @param m the backed map
     */
    @SuppressWarnings("unchecked")
    public TreeIntSet(NavigableIntObjectMap<?> m)
    {
        this.m = (NavigableIntObjectMap<Object>) m;
    }

    /**
     * Constructs a new, empty tree set, sorted according to the
     * natural ordering of its elements.  All elements inserted into
     * the set must implement the {@link Comparable} interface.
     * Furthermore, all such elements must be <i>mutually
     * comparable</i>: {@code e1.compareTo(e2)} must not throw a
     * {@code ClassCastException} for any elements {@code e1} and
     * {@code e2} in the set.  If the user attempts to add an element
     * to the set that violates this constraint (for example, the user
     * attempts to add a string element to a set whose elements are
     * integers), the {@code add} call will throw a
     * {@code ClassCastException}.
     */
    public TreeIntSet()
    {
        this(new TreeIntObjectMap<Object>());
    }

    /**
     * Constructs a new, empty tree set, sorted according to the specified
     * comparator.  All elements inserted into the set must be <i>mutually
     * comparable</i> by the specified comparator: {@code comparator.compare(e1,
     *e2)} must not throw a {@code ClassCastException} for any elements
     * {@code e1} and {@code e2} in the set.  If the user attempts to add
     * an element to the set that violates this constraint, the
     * {@code add} call will throw a {@code ClassCastException}.
     *
     * @param comparator the comparator that will be used to order this set.
     *                   If {@code null}, the {@linkplain Comparable natural
     *                   ordering} of the elements will be used.
     */
    public TreeIntSet(IntComparator comparator)
    {
        this(new TreeIntObjectMap<Object>(comparator));
    }

    /**
     * Constructs a new tree set containing the elements in the specified
     * collection, sorted according to the <i>natural ordering</i> of its
     * elements.  All elements inserted into the set must implement the
     * {@link Comparable} interface.  Furthermore, all such elements must be
     * <i>mutually comparable</i>: {@code e1.compareTo(e2)} must not throw a
     * {@code ClassCastException} for any elements {@code e1} and
     * {@code e2} in the set.
     *
     * @param c collection whose elements will comprise the new set
     * @throws ClassCastException   if the elements in {@code c} are
     *                              not {@link Comparable}, or are not mutually comparable
     * @throws NullPointerException if the specified collection is null
     */
    public TreeIntSet(IntCollection c)
    {
        this();
        addAll(c);
    }

    /**
     * Constructs a new tree set containing the same elements and
     * using the same ordering as the specified sorted set.
     *
     * @param s sorted set whose elements will comprise the new set
     * @throws NullPointerException if the specified sorted set is null
     */
    public TreeIntSet(SortedIntSet s)
    {
        this(s.comparator());
        addAll(s);
    }

    /**
     * Returns an iterator over the elements in this set in ascending order.
     *
     * @return an iterator over the elements in this set in ascending order
     */
    public IntIterator iterator()
    {
        return m.navigableKeySet().iterator();
    }

    /**
     * Returns an iterator over the elements in this set in descending order.
     *
     * @return an iterator over the elements in this set in descending order
     * @since 1.0.0
     */
    public IntIterator descendingIterator()
    {
        return m.descendingKeySet().iterator();
    }

    /**
     * @since 1.0.0
     */
    public NavigableIntSet descendingSet()
    {
        return new TreeIntSet(m.descendingMap());
    }

    /**
     * Returns the number of elements in this set (its cardinality).
     *
     * @return the number of elements in this set (its cardinality)
     */
    public int size()
    {
        return m.size();
    }

    /**
     * Returns {@code true} if this set contains no elements.
     *
     * @return {@code true} if this set contains no elements
     */
    public boolean isEmpty()
    {
        return m.isEmpty();
    }

    /**
     * Returns {@code true} if this set contains the specified element.
     * More formally, returns {@code true} if and only if this set
     * contains an element {@code e} such that
     * (o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e)).
     *
     * @param o object to be checked for containment in this set
     * @return {@code true} if this set contains the specified element
     * @throws ClassCastException   if the specified object cannot be compared
     *                              with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *                              and this set uses natural ordering, or its comparator
     *                              does not permit null elements
     */
    public boolean contains(int o)
    {
        return m.containsKey(o);
    }

    /**
     * Adds the specified element to this set if it is not already present.
     * More formally, adds the specified element {@code e} to this set if
     * the set contains no element {@code e2} such that
     * (e==null&nbsp;?&nbsp;e2==null&nbsp;:&nbsp;e.equals(e2)).
     * If this set already contains the element, the call leaves the set
     * unchanged and returns {@code false}.
     *
     * @param e element to be added to this set
     * @return {@code true} if this set did not already contain the specified
     *         element
     * @throws ClassCastException   if the specified object cannot be compared
     *                              with the elements currently in this set
     * @throws NullPointerException if the specified element is null
     *                              and this set uses natural ordering, or its comparator
     *                              does not permit null elements
     */
    public boolean add(int e)
    {
        return m.put(e, PRESENT) == null;
    }

    /**
     * Removes the specified element from this set if it is present.
     * More formally, removes an element {@code e} such that
     * (o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e)),
     * if this set contains such an element.  Returns {@code true} if
     * this set contained the element (or equivalently, if this set
     * changed as a result of the call).  (This set will not contain the
     * element once the call returns.)
     *
     * @param o object to be removed from this set, if present
     * @return {@code true} if this set contained the specified element
     * @throws ClassCastException   if the specified object cannot be compared
     *                              with the elements currently in this set
     * @throws NullPointerException if the specified element is null
     *                              and this set uses natural ordering, or its comparator
     *                              does not permit null elements
     */
    public boolean remove(int o)
    {
        return m.remove(o) == PRESENT;
    }

    /**
     * Removes all of the elements from this set.
     * The set will be empty after this call returns.
     */
    public void clear()
    {
        m.clear();
    }

    /**
     * Adds all of the elements in the specified collection to this set.
     *
     * @param c collection containing elements to be added to this set
     * @return {@code true} if this set changed as a result of the call
     * @throws ClassCastException   if the elements provided cannot be compared
     *                              with the elements currently in the set
     * @throws NullPointerException if the specified collection is null or
     *                              if any element is null and this set uses natural ordering, or
     *                              its comparator does not permit null elements
     */
    public boolean addAll(IntCollection c)
    {
        // Use linear-time version if applicable
        if(m.size() == 0 && c.size() > 0 && c instanceof SortedIntSet && m instanceof TreeIntObjectMap)
        {
            SortedIntSet set = (SortedIntSet) c;
            TreeIntObjectMap<Object> map = (TreeIntObjectMap<Object>) m;
            IntComparator cc = set.comparator();
            IntComparator mc = map.comparator();
            if(cc == mc || (cc != null && cc.equals(mc)))
            {
                map.addAllForTreeSet(set, PRESENT);
                return true;
            }
        }
        return super.addAll(c);
    }

    /**
     * @throws ClassCastException	   {@inheritDoc}
     * @throws NullPointerException	 if {@code fromElement} or {@code toElement}
     *                                  is null and this set uses natural ordering, or its comparator
     *                                  does not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.0.0
     */
    public NavigableIntSet subSet(int fromElement, boolean fromInclusive, int toElement, boolean toInclusive)
    {
        return new TreeIntSet(m.subMap(fromElement, fromInclusive, toElement, toInclusive));
    }

    /**
     * @throws ClassCastException	   {@inheritDoc}
     * @throws NullPointerException	 if {@code toElement} is null and
     *                                  this set uses natural ordering, or its comparator does
     *                                  not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.0.0
     */
    public NavigableIntSet headSet(int toElement, boolean inclusive)
    {
        return new TreeIntSet(m.headMap(toElement, inclusive));
    }

    /**
     * @throws ClassCastException	   {@inheritDoc}
     * @throws NullPointerException	 if {@code fromElement} is null and
     *                                  this set uses natural ordering, or its comparator does
     *                                  not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.0.0
     */
    public NavigableIntSet tailSet(int fromElement, boolean inclusive)
    {
        return new TreeIntSet(m.tailMap(fromElement, inclusive));
    }

    /**
     * @throws ClassCastException	   {@inheritDoc}
     * @throws NullPointerException	 if {@code fromElement} or
     *                                  {@code toElement} is null and this set uses natural ordering,
     *                                  or its comparator does not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedIntSet subSet(int fromElement, int toElement)
    {
        return subSet(fromElement, true, toElement, false);
    }

    /**
     * @throws ClassCastException	   {@inheritDoc}
     * @throws NullPointerException	 if {@code toElement} is null
     *                                  and this set uses natural ordering, or its comparator does
     *                                  not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedIntSet headSet(int toElement)
    {
        return headSet(toElement, false);
    }

    /**
     * @throws ClassCastException	   {@inheritDoc}
     * @throws NullPointerException	 if {@code fromElement} is null
     *                                  and this set uses natural ordering, or its comparator does
     *                                  not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedIntSet tailSet(int fromElement)
    {
        return tailSet(fromElement, true);
    }

    public IntComparator comparator()
    {
        return m.comparator();
    }

    /**
     * @throws java.util.NoSuchElementException
     *          {@inheritDoc}
     */
    public int first()
    {
        return m.firstKey();
    }

    /**
     * @throws java.util.NoSuchElementException
     *          {@inheritDoc}
     */
    public int last()
    {
        return m.lastKey();
    }

    // NavigableSet API methods

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified element is null
     *                              and this set uses natural ordering, or its comparator
     *                              does not permit null elements
     * @since 1.0.0
     */
    public int lower(int e)
    {
        return m.lowerKey(e);
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified element is null
     *                              and this set uses natural ordering, or its comparator
     *                              does not permit null elements
     * @since 1.0.0
     */
    public int floor(int e)
    {
        return m.floorKey(e);
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified element is null
     *                              and this set uses natural ordering, or its comparator
     *                              does not permit null elements
     * @since 1.0.0
     */
    public int ceiling(int e)
    {
        return m.ceilingKey(e);
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified element is null
     *                              and this set uses natural ordering, or its comparator
     *                              does not permit null elements
     * @since 1.0.0
     */
    public int higher(int e)
    {
        return m.higherKey(e);
    }

    /**
     * @since 1.0.0
     */
    public int pollFirst()
    {
        IntObjectPair<?> e = m.pollFirstEntry();
        return (e == null) ? null : e.getKey();
    }

    /**
     * @since 1.0.0
     */
    public int pollLast()
    {
        IntObjectPair<?> e = m.pollLastEntry();
        return (e == null) ? null : e.getKey();
    }

    /**
     * Returns a shallow copy of this {@code TreeSet} instance. (The elements
     * themselves are not cloned.)
     *
     * @return a shallow copy of this set
     */
    public Object clone()
    {
        TreeIntSet clone = null;
        try
        {
            clone = (TreeIntSet) super.clone();
        }
        catch(CloneNotSupportedException e)
        {
            throw new InternalError();
        }

        clone.m = new TreeIntObjectMap<Object>(m);
        return clone;
    }

    /**
     * Save the state of the {@code TreeSet} instance to a stream (that is,
     * serialize it).
     *
     * @serialData Emits the comparator used to order this set, or
     * {@code null} if it obeys its elements' natural ordering
     * (Object), followed by the size of the set (the number of
     * elements it contains) (int), followed by all of its
     * elements (each an Object) in order (as determined by the
     * set's Comparator, or by the elements' natural ordering if
     * the set has no Comparator).
     *
     * @param s the stream
     * @throws java.io.IOException if the stream throws a exception
     */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException
    {
        // Write out any hidden stuff
        s.defaultWriteObject();

        // Write out Comparator
        s.writeObject(m.comparator());

        // Write out size
        s.writeInt(m.size());

        // Write out all elements in the proper order.
        for(IntIterator i = m.keySet().iterator(); i.hasNext();)
        {
            s.writeInt(i.next());
        }
    }

    /**
     * Reconstitute the {@code TreeSet} instance from a stream (that is,
     * deserialize it).
     *
     * @param s the stream
     * @throws java.io.IOException if the stream throws a exception
     * @throws  ClassNotFoundException it the stream represent a unknown class
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException
    {
        // Read in any hidden stuff
        s.defaultReadObject();

        // Read in Comparator
        IntComparator c = (IntComparator) s.readObject();

        // Create backing TreeMap
        TreeIntObjectMap<Object> tm;
        if(c == null)
        {
            tm = new TreeIntObjectMap<Object>();
        }
        else
        {
            tm = new TreeIntObjectMap<Object>(c);
        }
        m = tm;

        // Read in size
        int size = s.readInt();

        tm.readTreeSet(size, s, PRESENT);
    }

}