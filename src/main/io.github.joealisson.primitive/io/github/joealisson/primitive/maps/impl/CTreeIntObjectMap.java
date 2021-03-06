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
package io.github.joealisson.primitive.maps.impl;

import java.io.IOException;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.github.joealisson.primitive.pair.IntObjectPair;
import io.github.joealisson.primitive.pair.impl.ImmutableIntObjectPairImpl;
import io.github.joealisson.primitive.Comparators;
import io.github.joealisson.primitive.collections.IntCollection;
import io.github.joealisson.primitive.comparators.IntComparator;
import io.github.joealisson.primitive.iterators.IntIterator;
import io.github.joealisson.primitive.maps.CNavigableIntObjectMap;
import io.github.joealisson.primitive.maps.IntObjectMap;
import io.github.joealisson.primitive.maps.SortedIntObjectMap;
import io.github.joealisson.primitive.maps.abstracts.AbstractIntObjectMap;
import io.github.joealisson.primitive.sets.IntSet;
import io.github.joealisson.primitive.sets.NavigableIntSet;
import io.github.joealisson.primitive.sets.abstracts.AbstractIntSet;
import io.github.joealisson.primitive.sets.impl.CTreeIntSet;

/**
 * <p>
 * A scalable concurrent {@link CNavigableIntObjectMap} implementation.
 * The map is sorted according to the {@linkplain Comparable natural
 * ordering} of its keys, or by a {@link Comparator} provided at map
 * creation time, depending on which constructor is used.
 * </p>
 * <p>This class implements a concurrent variant of <a
 * href="http://www.cs.umd.edu/~pugh/">SkipLists</a> providing
 * expected average <i>log(n)</i> time cost for the
 * containsKey, get, put and
 * remove operations and their variants.  Insertion, removal,
 * update, and access operations safely execute concurrently by
 * multiple threads.  Iterators are <i>weakly consistent</i>, returning
 * elements reflecting the state of the map at some point at or since
 * the creation of the iterator.  They do <em>not</em> throw {@link
 * ConcurrentModificationException}, and may proceed concurrently with
 * other operations. Ascending key ordered views and their iterators
 * are faster than descending ones.
 * </p>
 * <p>All Map.Entry pairs returned by methods in this class
 * and its views represent snapshots of mappings at the time they were
 * produced. They do <em>not</em> support the Entry.setValue
 * method. (Note however that it is possible to change mappings in the
 * associated map using put, putIfAbsent, or
 * replace, depending on exactly which effect you need.)
 * </p>
 * <p>Beware that, unlike in most collections, the size
 * method is <em>not</em> a constant-time operation. Because of the
 * asynchronous nature of these maps, determining the current number
 * of elements requires a traversal of the elements.  Additionally,
 * the bulk operations putAll, equals, and
 * clear are <em>not</em> guaranteed to be performed
 * atomically. For example, an iterator operating concurrently with a
 * putAll operation might view only some of the added
 * elements.
 * </p>
 * <p>This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link IntObjectMap} and {@link IntIterator}
 * interfaces. Like most other concurrent collections, this class does
 * <em>not</em> permit the use of null keys or values because some
 * null return values cannot be reliably distinguished from the absence of
 * elements.
 * </p>
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <V> the type of mapped values
 * @author Doug Lea
 * @since 1.0.0
 */
public class CTreeIntObjectMap<V> extends AbstractIntObjectMap<V> implements CNavigableIntObjectMap<V>, Cloneable, java.io.Serializable
{
	/*
		 * This class implements a tree-like two-dimensionally linked skip
		 * list in which the index levels are represented in separate
		 * nodes from the base nodes holding data.  There are two reasons
		 * for taking this approach instead of the usual array-based
		 * structure: 1) Array based implementations seem to encounter
		 * more complexity and overhead 2) We can use cheaper algorithms
		 * for the heavily-traversed index lists than can be used for the
		 * base lists.  Here's a picture of some of the basics for a
		 * possible list with 2 levels of index:
		 *
		 * Head nodes          Index nodes
		 * +-+    right        +-+                      +-+
		 * |2|---------------->| |--------------------->| |->null
		 * +-+                 +-+                      +-+
		 *  | down              |                        |
		 *  v                   v                        v
		 * +-+            +-+  +-+       +-+            +-+       +-+
		 * |1|----------->| |->| |------>| |----------->| |------>| |->null
		 * +-+            +-+  +-+       +-+            +-+       +-+
		 *  v              |    |         |              |         |
		 * Nodes  next     v    v         v              v         v
		 * +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+
		 * | |->|A|->|B|->|C|->|D|->|E|->|F|->|G|->|H|->|I|->|J|->|K|->null
		 * +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+  +-+
		 *
		 * The base lists use a variant of the HM linked ordered set
		 * algorithm. See Tim Harris, "A pragmatic implementation of
		 * non-blocking linked lists"
		 * http://www.cl.cam.ac.uk/~tlh20/publications.html and Maged
		 * Michael "High Performance Dynamic Lock-Free Hash Tables and
		 * List-Based Sets"
		 * http://www.research.ibm.com/people/m/michael/pubs.htm.  The
		 * basic idea in these lists is to mark the "next" pointers of
		 * deleted nodes when deleting to avoid conflicts with concurrent
		 * insertions, and when traversing to keep track of triples
		 * (predecessor, node, successor) in order to detect when and how
		 * to unlink these deleted nodes.
		 *
		 * Rather than using mark-bits to mark list deletions (which can
		 * be slow and space-intensive using AtomicMarkedReference), nodes
		 * use direct CAS'able next pointers.  On deletion, instead of
		 * marking a pointer, they splice in another node that can be
		 * thought of as standing for a marked pointer (indicating this by
		 * using otherwise impossible field values).  Using plain nodes
		 * acts roughly like "boxed" implementations of marked pointers,
		 * but uses new nodes only when nodes are deleted, not for every
		 * link.  This requires less space and supports faster
		 * traversal. Even if marked references were better supported by
		 * JVMs, traversal using this technique might still be faster
		 * because any search need only read ahead one more node than
		 * otherwise required (to check for trailing marker) rather than
		 * unmasking mark bits or whatever on each read.
		 *
		 * This approach maintains the essential property needed in the HM
		 * algorithm of changing the next-pointer of a deleted node so
		 * that any other CAS of it will fail, but implements the idea by
		 * changing the pointer to point to a different node, not by
		 * marking it.  While it would be possible to further squeeze
		 * space by defining marker nodes not to have key/value fields, it
		 * isn't worth the extra type-testing overhead.  The deletion
		 * markers are rarely encountered during traversal and are
		 * normally quickly garbage collected. (Note that this technique
		 * would not work well in systems without garbage collection.)
		 *
		 * In addition to using deletion markers, the lists also use
		 * nullness of value fields to indicate deletion, in a style
		 * similar to typical lazy-deletion schemes.  If a node's value is
		 * null, then it is considered logically deleted and ignored even
		 * though it is still reachable. This maintains proper control of
		 * concurrent replace vs delete operations -- an attempted replace
		 * must fail if a delete beat it by nulling field, and a delete
		 * must return the last non-null value held in the field. (Note:
		 * Null, rather than some special marker, is used for value fields
		 * here because it just so happens to mesh with the Map API
		 * requirement that method get returns null if there is no
		 * mapping, which allows nodes to remain concurrently readable
		 * even when deleted. Using any other marker value here would be
		 * messy at best.)
		 *
		 * Here's the sequence of events for a deletion of node n with
		 * predecessor b and successor f, initially:
		 *
		 *        +------+       +------+      +------+
		 *   ...  |   b  |------>|   n  |----->|   f  | ...
		 *        +------+       +------+      +------+
		 *
		 * 1. CAS n's value field from non-null to null.
		 *    From this point on, no public operations encountering
		 *    the node consider this mapping to exist. However, other
		 *    ongoing insertions and deletions might still modify
		 *    n's next pointer.
		 *
		 * 2. CAS n's next pointer to point to a new marker node.
		 *    From this point on, no other nodes can be appended to n.
		 *    which avoids deletion errors in CAS-based linked lists.
		 *
		 *        +------+       +------+      +------+       +------+
		 *   ...  |   b  |------>|   n  |----->|marker|------>|   f  | ...
		 *        +------+       +------+      +------+       +------+
		 *
		 * 3. CAS b's next pointer over both n and its marker.
		 *    From this point on, no new traversals will encounter n,
		 *    and it can eventually be GCed.
		 *        +------+                                    +------+
		 *   ...  |   b  |----------------------------------->|   f  | ...
		 *        +------+                                    +------+
		 *
		 * A failure at step 1 leads to simple retry due to a lost race
		 * with another operation. Steps 2-3 can fail because some other
		 * thread noticed during a traversal a node with null value and
		 * helped out by marking and/or unlinking.  This helping-out
		 * ensures that no thread can become stuck waiting for progress of
		 * the deleting thread.  The use of marker nodes slightly
		 * complicates helping-out code because traversals must track
		 * consistent reads of up to four nodes (b, n, marker, f), not
		 * just (b, n, f), although the next field of a marker is
		 * immutable, and once a next field is CAS'ed to point to a
		 * marker, it never again changes, so this requires less care.
		 *
		 * Skip lists add indexing to this scheme, so that the base-level
		 * traversals start close to the locations being found, inserted
		 * or deleted -- usually base level traversals only traverse a few
		 * nodes. This doesn't change the basic algorithm except for the
		 * need to make sure base traversals start at predecessors (here,
		 * b) that are not (structurally) deleted, otherwise retrying
		 * after processing the deletion.
		 *
		 * Index levels are maintained as lists with volatile next fields,
		 * using CAS to link and unlink.  Races are allowed in index-list
		 * operations that can (rarely) fail to link in a new index node
		 * or delete one. (We can't do this of course for data nodes.)
		 * However, even when this happens, the index lists remain sorted,
		 * so correctly serve as indices.  This can impact performance,
		 * but since skip lists are probabilistic anyway, the net result
		 * is that under contention, the effective "p" value may be lower
		 * than its nominal value. And race windows are kept small enough
		 * that in practice these failures are rare, even under a lot of
		 * contention.
		 *
		 * The fact that retries (for both base and index lists) are
		 * relatively cheap due to indexing allows some minor
		 * simplifications of retry logic. Traversal restarts are
		 * performed after most "helping-out" CASes. This isn't always
		 * strictly necessary, but the implicit backoffs tend to help
		 * reduce other downstream failed CAS's enough to outweigh restart
		 * cost.  This worsens the worst case, but seems to improve even
		 * highly contended cases.
		 *
		 * Unlike most skip-list implementations, index insertion and
		 * deletion here require a separate traversal pass occuring after
		 * the base-level action, to add or remove index nodes.  This adds
		 * to single-threaded overhead, but improves contended
		 * multithreaded performance by narrowing interference windows,
		 * and allows deletion to ensure that all index nodes will be made
		 * unreachable upon return from a public remove operation, thus
		 * avoiding unwanted garbage retention. This is more important
		 * here than in some other data structures because we cannot null
		 * out node fields referencing user keys since they might still be
		 * read by other ongoing traversals.
		 *
		 * Indexing uses skip list parameters that maintain good search
		 * performance while using sparser-than-usual indices: The
		 * hardwired parameters k=1, p=0.5 (see method randomLevel) mean
		 * that about one-quarter of the nodes have indices. Of those that
		 * do, half have one level, a quarter have two, and so on (see
		 * Pugh's Skip List Cookbook, sec 3.4).  The expected total space
		 * requirement for a map is slightly less than for the current
		 * implementation of java.util.TreeMap.
		 *
		 * Changing the level of the index (i.e, the height of the
		 * tree-like structure) also uses CAS. The head index has initial
		 * level/height of one. Creation of an index with height greater
		 * than the current level adds a level to the head index by
		 * CAS'ing on a new top-most head. To maintain good performance
		 * after a lot of removals, deletion methods heuristically try to
		 * reduce the height if the topmost levels appear to be empty.
		 * This may encounter races in which it possible (but rare) to
		 * reduce and "lose" a level just as it is about to contain an
		 * index (that will then never be encountered). This does no
		 * structural harm, and in practice appears to be a better option
		 * than allowing unrestrained growth of levels.
		 *
		 * The code for all this is more verbose than you'd like. Most
		 * operations entail locating an element (or position to insert an
		 * element). The code to do this can't be nicely factored out
		 * because subsequent uses require a snapshot of predecessor
		 * and/or successor and/or value fields which can't be returned
		 * all at once, at least not without creating yet another object
		 * to hold them -- creating such little objects is an especially
		 * bad idea for basic internal search operations because it adds
		 * to GC overhead.  (This is one of the few times I've wished Java
		 * had macros.) Instead, some traversal code is interleaved within
		 * insertion and removal operations.  The control logic to handle
		 * all the retry conditions is sometimes twisty. Most search is
		 * broken into 2 parts. findPredecessor() searches index nodes
		 * only, returning a base-level predecessor of the key. findNode()
		 * finishes out the base-level search. Even with this factoring,
		 * there is a fair amount of near-duplication of code to handle
		 * variants.
		 *
		 * For explanation of algorithms sharing at least a couple of
		 * features with this one, see Mikhail Fomitchev's thesis
		 * (http://www.cs.yorku.ca/~mikhail/), Keir Fraser's thesis
		 * (http://www.cl.cam.ac.uk/users/kaf24/), and Hakan Sundell's
		 * thesis (http://www.cs.chalmers.se/~phs/).
		 *
		 * Given the use of tree-like index nodes, you might wonder why
		 * this doesn't use some kind of search tree instead, which would
		 * support somewhat faster search operations. The reason is that
		 * there are no known efficient lock-free insertion and deletion
		 * algorithms for search trees. The immutability of the "down"
		 * links of index nodes (as opposed to mutable "left" fields in
		 * true trees) makes this tractable using only CAS operations.
		 *
		 * Notation guide for local variables
		 * Node:         b, n, f    for  predecessor, node, successor
		 * Index:        q, r, d    for index node, right, down.
		 *               t          for another index node
		 * Head:         h
		 * Levels:       j
		 * Keys:         k, key
		 * Values:       v, value
		 * Comparisons:  c
		 */

	/**
	 * Generates the initial random seed for the cheaper per-instance
	 * random number generators used in randomLevel.
	 */
	private static final Random seedGenerator = new Random();

	/**
	 * Special value used to identify base-level header
	 */
	private static final Object BASE_HEADER = new Object();

	/**
	 * The topmost head index of the skiplist.
	 */
	private transient volatile HeadIndex<V> head;

	/**
	 * The comparator used to maintain order in this map, or null
	 * if using natural ordering.
	 *
	 * @serial
	 */
	private final IntComparator comparator;

	/**
	 * Seed for simple random number generator.  Not volatile since it
	 * doesn't matter too much if different threads don't see updates.
	 */
	private transient int randomSeed;

	/**
	 * Lazily initialized key set
	 */
	private transient KeySet keySet;
	/**
	 * Lazily initialized entry set
	 */
	@SuppressWarnings("rawtypes")
	private transient EntrySet entrySet;
	/**
	 * Lazily initialized values collection
	 */
	@SuppressWarnings("rawtypes")
	private transient Values values;
	/**
	 * Lazily initialized descending key set
	 */
	private transient CNavigableIntObjectMap<V> descendingMap;

	/**
	 * Initializes or resets state. Needed by constructors, clone,
	 * clear, readObject. and ConcurrentSkipListSet.clone.
	 * (Note that comparator must be separately initialized.)
	 */
	final void initialize()
	{
		keySet = null;
		entrySet = null;
		values = null;
		descendingMap = null;
		randomSeed = seedGenerator.nextInt() | 0x0100; // ensure nonzero
		head = new HeadIndex<V>(new Node<V>(0, BASE_HEADER, null), null, null, 1);
	}

	/**
	 * Updater for casHead
	 */
	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<CTreeIntObjectMap, HeadIndex> headUpdater = AtomicReferenceFieldUpdater.newUpdater(CTreeIntObjectMap.class, HeadIndex.class, "head");

	/**
	 * compareAndSet head node
	 */
	private boolean casHead(HeadIndex<V> cmp, HeadIndex<V> val)
	{
		return headUpdater.compareAndSet(this, cmp, val);
	}

	/* ---------------- Nodes -------------- */

	/**
	 * Nodes hold keys and values, and are singly linked in sorted
	 * order, possibly with some intervening marker nodes. The list is
	 * headed by a dummy node accessible as head.node. The value field
	 * is declared only as Object because it takes special non-V
	 * values for marker and header nodes.
	 */
	static final class Node<V>
	{
		final int key;
		volatile Object value;
		volatile Node<V> next;

		/**
		 * Creates a new regular node.
		 */
		Node(int key, Object value, Node<V> next)
		{
			this.key = key;
			this.value = value;
			this.next = next;
		}

		/**
		 * Creates a new marker node. A marker is distinguished by
		 * having its value field point to itself.  Marker nodes also
		 * have null keys, a fact that is exploited in a few places,
		 * but this doesn't distinguish markers from the base-level
		 * header node (head.node), which also has a null key.
		 */
		Node(Node<V> next)
		{
			this.key = 0;
			this.value = this;
			this.next = next;
		}

		/**
		 * Updater for casNext
		 */
		@SuppressWarnings("rawtypes")
		static final AtomicReferenceFieldUpdater<Node, Node> nextUpdater = AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "next");

		/**
		 * Updater for casValue
		 */
		@SuppressWarnings("rawtypes")
		static final AtomicReferenceFieldUpdater<Node, Object> valueUpdater = AtomicReferenceFieldUpdater.newUpdater(Node.class, Object.class, "value");

		/**
		 * compareAndSet value field
		 */
		boolean casValue(Object cmp, Object val)
		{
			return valueUpdater.compareAndSet(this, cmp, val);
		}

		/**
		 * compareAndSet next field
		 */
		boolean casNext(Node<V> cmp, Node<V> val)
		{
			return nextUpdater.compareAndSet(this, cmp, val);
		}

		/**
		 * Returns true if this node is a marker. This method isn't
		 * actually called in any current code checking for markers
		 * because callers will have already read value field and need
		 * to use that read (not another done here) and so directly
		 * test if value points to node.
		 *
		 * @param n a possibly null reference to a node
		 * @return true if this node is a marker node
		 */
		boolean isMarker()
		{
			return value == this;
		}

		/**
		 * Returns true if this node is the header of base-level list.
		 *
		 * @return true if this node is header node
		 */
		boolean isBaseHeader()
		{
			return value == BASE_HEADER;
		}

		/**
		 * Tries to append a deletion marker to this node.
		 *
		 * @param f the assumed current successor of this node
		 * @return true if successful
		 */
		boolean appendMarker(Node<V> f)
		{
			return casNext(f, new Node<V>(f));
		}

		/**
		 * Helps out a deletion by appending marker or unlinking from
		 * predecessor. This is called during traversals when value
		 * field seen to be null.
		 *
		 * @param b predecessor
		 * @param f successor
		 */
		void helpDelete(Node<V> b, Node<V> f)
		{
			/*
						 * Rechecking links and then doing only one of the
						 * help-out stages per call tends to minimize CAS
						 * interference among helping threads.
						 */
			if(f == next && this == b.next)
			{
				if(f == null || f.value != f) // not already marked
				{
					appendMarker(f);
				}
				else
				{
					b.casNext(this, f.next);
				}
			}
		}

		/**
		 * Returns value if this node contains a valid key-value pair,
		 * else null.
		 *
		 * @return this node's value if it isn't a marker or header or
		 *         is deleted, else null.
		 */
		@SuppressWarnings("unchecked")
		V getValidValue()
		{
			Object v = value;
			if(v == this || v == BASE_HEADER)
			{
				return null;
			}
			return (V) v;
		}

		/**
		 * Creates and returns a new SimpleImmutableEntry holding current
		 * mapping if this node holds a valid value, else null.
		 *
		 * @return new entry or null
		 */
		IntObjectPair<V> createSnapshot()
		{
			V v = getValidValue();
			if(v == null)
			{
				return null;
			}
			return new ImmutableIntObjectPairImpl<V>(key, v);
		}
	}

	/* ---------------- Indexing -------------- */

	/**
	 * Index nodes represent the levels of the skip list.  Note that
	 * even though both Nodes and Indexes have forward-pointing
	 * fields, they have different types and are handled in different
	 * ways, that can't nicely be captured by placing field in a
	 * shared abstract class.
	 */
	static class Index<V>
	{
		final Node<V> node;
		final Index<V> down;
		volatile Index<V> right;

		/**
		 * Creates index node with given values.
		 */
		Index(Node<V> node, Index<V> down, Index<V> right)
		{
			this.node = node;
			this.down = down;
			this.right = right;
		}

		/**
		 * Updater for casRight
		 */
		@SuppressWarnings("rawtypes")
		static final AtomicReferenceFieldUpdater<Index, Index> rightUpdater = AtomicReferenceFieldUpdater.newUpdater(Index.class, Index.class, "right");

		/**
		 * compareAndSet right field
		 */
		final boolean casRight(Index<V> cmp, Index<V> val)
		{
			return rightUpdater.compareAndSet(this, cmp, val);
		}

		/**
		 * Returns true if the node this indexes has been deleted.
		 *
		 * @return true if indexed node is known to be deleted
		 */
		final boolean indexesDeletedNode()
		{
			return node.value == null;
		}

		/**
		 * Tries to CAS newSucc as successor.  To minimize races with
		 * unlink that may lose this index node, if the node being
		 * indexed is known to be deleted, it doesn't try to link in.
		 *
		 * @param succ	the expected current successor
		 * @param newSucc the new successor
		 * @return true if successful
		 */
		final boolean link(Index<V> succ, Index<V> newSucc)
		{
			Node<V> n = node;
			newSucc.right = succ;
			return n.value != null && casRight(succ, newSucc);
		}

		/**
		 * Tries to CAS right field to skip over apparent successor
		 * succ.  Fails (forcing a retraversal by caller) if this node
		 * is known to be deleted.
		 *
		 * @param succ the expected current successor
		 * @return true if successful
		 */
		final boolean unlink(Index<V> succ)
		{
			return !indexesDeletedNode() && casRight(succ, succ.right);
		}
	}

	/* ---------------- Head nodes -------------- */

	/**
	 * Nodes heading each level keep track of their level.
	 */
	static final class HeadIndex<V> extends Index<V>
	{
		final int level;

		HeadIndex(Node<V> node, Index<V> down, Index<V> right, int level)
		{
			super(node, down, right);
			this.level = level;
		}
	}

	/* ---------------- Comparison utilities -------------- */

	/**
	 * Compares using comparator or natural ordering. Used when the
	 * ComparableUsingComparator approach doesn't apply.
	 */
	int compare(int k1, int k2) throws ClassCastException
	{
		IntComparator cmp = comparator;
		if(cmp != null)
		{
			return cmp.compare(k1, k2);
		}
		else
		{
			return Comparators.DEFAULT_INT_COMPARATOR.compare(k1, k2);
		}
	}

	/**
	 * Returns true if given key greater than or equal to least and
	 * strictly less than fence, bypassing either test if least or
	 * fence are null. Needed mainly in submap operations.
	 */
	boolean inHalfOpenRange(int key, int least, int fence)
	{
		return ((compare(key, least) >= 0) && (compare(key, fence) < 0));
	}

	/**
	 * Returns true if given key greater than or equal to least and less
	 * or equal to fence. Needed mainly in submap operations.
	 */
	boolean inOpenRange(int key, int least, int fence)
	{
		return ((compare(key, least) >= 0) && (compare(key, fence) <= 0));
	}

	/* ---------------- Traversal -------------- */

	/**
	 * Returns a base-level node with key strictly less than given key,
	 * or the base-level header if there is no such node.  Also
	 * unlinks indexes to deleted nodes found along the way.  Callers
	 * rely on this side-effect of clearing indices to deleted nodes.
	 *
	 * @param key the key
	 * @return a predecessor of key
	 */
	private Node<V> findPredecessor(int key)
	{
		for(; ;)
		{
			Index<V> q = head;
			Index<V> r = q.right;
			for(; ;)
			{
				if(r != null)
				{
					Node<V> n = r.node;
					int k = n.key;
					if(n.value == null)
					{
						if(!q.unlink(r))
						{
							break;		   // restart
						}
						r = q.right;		 // reread r
						continue;
					}
					if(compare(key, k) > 0)
					{
						q = r;
						r = r.right;
						continue;
					}
				}
				Index<V> d = q.down;
				if(d != null)
				{
					q = d;
					r = d.right;
				}
				else
				{
					return q.node;
				}
			}
		}
	}

	/**
	 * Returns node holding key or null if no such, clearing out any
	 * deleted nodes seen along the way.  Repeatedly traverses at
	 * base-level looking for key starting at predecessor returned
	 * from findPredecessor, processing base-level deletions as
	 * encountered. Some callers rely on this side-effect of clearing
	 * deleted nodes.
	 * </p>
	 * Restarts occur, at traversal step centered on node n, if:
	 * </p>
	 * (1) After reading n's next field, n is no longer assumed
	 * predecessor b's current successor, which means that
	 * we don't have a consistent 3-node snapshot and so cannot
	 * unlink any subsequent deleted nodes encountered.
	 * </p>
	 * (2) n's value field is null, indicating n is deleted, in
	 * which case we help out an ongoing structural deletion
	 * before retrying.  Even though there are cases where such
	 * unlinking doesn't require restart, they aren't sorted out
	 * here because doing so would not usually outweigh cost of
	 * restarting.
	 * </p>
	 * (3) n is a marker or n's predecessor's value field is null,
	 * indicating (among other possibilities) that
	 * findPredecessor returned a deleted node. We can't unlink
	 * the node because we don't know its predecessor, so rely
	 * on another call to findPredecessor to notice and return
	 * some earlier predecessor, which it will do. This check is
	 * only strictly needed at beginning of loop, (and the
	 * b.value check isn't strictly needed at all) but is done
	 * each iteration to help avoid contention with other
	 * threads by callers that will fail to be able to change
	 * links, and so will retry anyway.
	 * </p>
	 * The traversal loops in doPut, doRemove, and findNear all
	 * include the same three kinds of checks. And specialized
	 * versions appear in findFirst, and findLast and their
	 * variants. They can't easily share code because each uses the
	 * reads of fields held in locals occurring in the orders they
	 * were performed.
	 *
	 * @param key the key
	 * @return node holding key, or null if no such
	 */
	private Node<V> findNode(int key)
	{
		for(; ;)
		{
			Node<V> b = findPredecessor(key);
			Node<V> n = b.next;
			for(; ;)
			{
				if(n == null)
				{
					return null;
				}
				Node<V> f = n.next;
				if(n != b.next)				// inconsistent read
				{
					break;
				}
				Object v = n.value;
				if(v == null)
				{				// n is deleted
					n.helpDelete(b, f);
					break;
				}
				if(v == n || b.value == null)  // b is deleted
				{
					break;
				}
				int c = compare(key, n.key);
				if(c == 0)
				{
					return n;
				}
				if(c < 0)
				{
					return null;
				}
				b = n;
				n = f;
			}
		}
	}

	/**
	 * Specialized variant of findNode to perform IntObjectMap.get. Does a weak
	 * traversal, not bothering to fix any deleted index nodes,
	 * returning early if it happens to see key in index, and passing
	 * over any deleted base nodes, falling back to getUsingFindNode
	 * only if it would otherwise return value from an ongoing
	 * deletion. Also uses "bound" to eliminate need for some
	 * comparisons (see Pugh Cookbook). Also folds uses of null checks
	 * and node-skipping because markers have null keys.
	 *
	 * @param key the key
	 * @return the value, or null if absent
	 */
	@SuppressWarnings("unchecked")
	private V doGet(int key)
	{
		Node<V> bound = null;
		Index<V> q = head;
		Index<V> r = q.right;
		Node<V> n;
		int k;
		int c;
		for(; ;)
		{
			Index<V> d;
			// Traverse rights
			if(r != null && (n = r.node) != bound)
			{
				k = n.key;
				if((c = compare(key, k)) > 0)
				{
					q = r;
					r = r.right;
					continue;
				}
				else if(c == 0)
				{
					Object v = n.value;
					return (v != null) ? (V) v : getUsingFindNode(key);
				}
				else
				{
					bound = n;
				}
			}

			// Traverse down
			if((d = q.down) != null)
			{
				q = d;
				r = d.right;
			}
			else
			{
				break;
			}
		}

		// Traverse nexts
		for(n = q.node.next; n != null; n = n.next)
		{
			k = n.key;
			if((c = compare(key, k)) == 0)
			{
				Object v = n.value;
				return (v != null) ? (V) v : getUsingFindNode(key);
			}
			else if(c < 0)
			{
				break;
			}
		}
		return null;
	}

	/**
	 * Performs map.get via findNode.  Used as a backup if doGet
	 * encounters an in-progress deletion.
	 *
	 * @param key the key
	 * @return the value, or null if absent
	 */
	@SuppressWarnings("unchecked")
	private V getUsingFindNode(int key)
	{
		/*
				 * Loop needed here and elsewhere in case value field goes
				 * null just as it is about to be returned, in which case we
				 * lost a race with a deletion, so must retry.
				 */
		for(; ;)
		{
			Node<V> n = findNode(key);
			if(n == null)
			{
				return null;
			}
			Object v = n.value;
			if(v != null)
			{
				return (V) v;
			}
		}
	}

	/* ---------------- Insertion -------------- */

	/**
	 * Main insertion method.  Adds element if not present, or
	 * replaces value if present and onlyIfAbsent is false.
	 *
	 * @param kkey		 the key
	 * @param value		the value that must be associated with key
	 * @param onlyIfAbsent if should not insert if already present
	 * @return the old value, or null if newly inserted
	 */
	@SuppressWarnings("unchecked")
	private V doPut(int kkey, V value, boolean onlyIfAbsent)
	{
		for(; ;)
		{
			Node<V> b = findPredecessor(kkey);
			Node<V> n = b.next;
			for(; ;)
			{
				if(n != null)
				{
					Node<V> f = n.next;
					if(n != b.next)			   // inconsistent read
					{
						break;
					}

					Object v = n.value;
					if(v == null)
					{			   // n is deleted
						n.helpDelete(b, f);
						break;
					}
					if(v == n || b.value == null) // b is deleted
					{
						break;
					}
					int c = compare(kkey, n.key);
					if(c > 0)
					{
						b = n;
						n = f;
						continue;
					}
					if(c == 0)
					{
						if(onlyIfAbsent || n.casValue(v, value))
						{
							return (V) v;
						}
						else
						{
							break; // restart if lost race to replace value
						}
					}
					// else c < 0; fall through
				}

				Node<V> z = new Node<V>(kkey, value, n);
				if(!b.casNext(n, z))
				{
					break;		 // restart if lost race to append to b
				}
				int level = randomLevel();
				if(level > 0)
				{
					insertIndex(z, level);
				}
				return null;
			}
		}
	}

	/**
	 * Returns a random level for inserting a new node.
	 * Hardwired to k=1, p=0.5, max 31 (see above and
	 * Pugh's "Skip List Cookbook", sec 3.4).
	 * </p>
	 * This uses the simplest of the generators described in George
	 * Marsaglia's "Xorshift RNGs" paper.  This is not a high-quality
	 * generator but is acceptable here.
	 */
	private int randomLevel()
	{
		int x = randomSeed;
		x ^= x << 13;
		x ^= x >>> 17;
		randomSeed = x ^= x << 5;
		if((x & 0x8001) != 0) // test highest and lowest bits
		{
			return 0;
		}
		int level = 1;
		while(((x >>>= 1) & 1) != 0)
		{
			++level;
		}
		return level;
	}

	/**
	 * Creates and adds index nodes for the given node.
	 *
	 * @param z	 the node
	 * @param level the level of the index
	 */
	private void insertIndex(Node<V> z, int level)
	{
		HeadIndex<V> h = head;
		int max = h.level;

		if(level <= max)
		{
			Index<V> idx = null;
			for(int i = 1; i <= level; ++i)
			{
				idx = new Index<V>(z, idx, null);
			}
			addIndex(idx, h, level);

		}
		else
		{ // Add a new level
			/*
						 * To reduce interference by other threads checking for
						 * empty levels in tryReduceLevel, new levels are added
						 * with initialized right pointers. Which in turn requires
						 * keeping levels in an array to access them while
						 * creating new head index nodes from the opposite
						 * direction.
						 */
			level = max + 1;
			@SuppressWarnings("unchecked")
			Index<V>[] idxs = (Index<V>[]) new Index[level + 1];
			Index<V> idx = null;
			for(int i = 1; i <= level; ++i)
			{
				idxs[i] = idx = new Index<V>(z, idx, null);
			}

			HeadIndex<V> oldh;
			int k;
			for(; ;)
			{
				oldh = head;
				int oldLevel = oldh.level;
				if(level <= oldLevel)
				{ // lost race to add level
					k = level;
					break;
				}
				HeadIndex<V> newh = oldh;
				Node<V> oldbase = oldh.node;
				for(int j = oldLevel + 1; j <= level; ++j)
				{
					newh = new HeadIndex<V>(oldbase, newh, idxs[j], j);
				}
				if(casHead(oldh, newh))
				{
					k = oldLevel;
					break;
				}
			}
			addIndex(idxs[k], oldh, k);
		}
	}

	/**
	 * Adds given index nodes from given level down to 1.
	 *
	 * @param idx		the topmost index node being inserted
	 * @param h		  the value of head to use to insert. This must be
	 *                   snapshotted by callers to provide correct insertion level
	 * @param indexLevel the level of the index
	 */
	private void addIndex(Index<V> idx, HeadIndex<V> h, int indexLevel)
	{
		// Track next level to insert in case of retries
		int insertionLevel = indexLevel;
		int key = idx.node.key;

		// Similar to findPredecessor, but adding index nodes along
		// path to key.
		for(; ;)
		{
			int j = h.level;
			Index<V> q = h;
			Index<V> r = q.right;
			Index<V> t = idx;
			for(; ;)
			{
				if(r != null)
				{
					Node<V> n = r.node;
					// compare before deletion check avoids needing recheck
					int c = compare(key, n.key);
					if(n.value == null)
					{
						if(!q.unlink(r))
						{
							break;
						}
						r = q.right;
						continue;
					}
					if(c > 0)
					{
						q = r;
						r = r.right;
						continue;
					}
				}

				if(j == insertionLevel)
				{
					// Don't insert index if node already deleted
					if(t.indexesDeletedNode())
					{
						findNode(key); // cleans up
						return;
					}
					if(!q.link(r, t))
					{
						break; // restart
					}
					if(--insertionLevel == 0)
					{
						// need final deletion check before return
						if(t.indexesDeletedNode())
						{
							findNode(key);
						}
						return;
					}
				}

				if(--j >= insertionLevel && j < indexLevel)
				{
					t = t.down;
				}
				q = q.down;
				r = q.right;
			}
		}
	}

	/* ---------------- Deletion -------------- */

	/**
	 * Main deletion method. Locates node, nulls value, appends a
	 * deletion marker, unlinks predecessor, removes associated index
	 * nodes, and possibly reduces head index level.
	 * </p>
	 * Index nodes are cleared out simply by calling findPredecessor.
	 * which unlinks indexes to deleted nodes found along path to key,
	 * which will include the indexes to this node.  This is done
	 * unconditionally. We can't check beforehand whether there are
	 * index nodes because it might be the case that some or all
	 * indexes hadn't been inserted yet for this node during initial
	 * search for it, and we'd like to ensure lack of garbage
	 * retention, so must call to be sure.
	 *
	 * @param okey  the key
	 * @param value if non-null, the value that must be
	 *              associated with key
	 * @return the node, or null if not found
	 */
	@SuppressWarnings("unchecked")
	final V doRemove(int okey, Object value)
	{
		for(; ;)
		{
			Node<V> b = findPredecessor(okey);
			Node<V> n = b.next;
			for(; ;)
			{
				if(n == null)
				{
					return null;
				}
				Node<V> f = n.next;
				if(n != b.next)					// inconsistent read
				{
					break;
				}
				Object v = n.value;
				if(v == null)
				{					// n is deleted
					n.helpDelete(b, f);
					break;
				}
				if(v == n || b.value == null)	  // b is deleted
				{
					break;
				}
				int c = compare(okey, n.key);
				if(c < 0)
				{
					return null;
				}
				if(c > 0)
				{
					b = n;
					n = f;
					continue;
				}
				if(value != null && !value.equals(v))
				{
					return null;
				}
				if(!n.casValue(v, null))
				{
					break;
				}
				if(!n.appendMarker(f) || !b.casNext(n, f))
				{
					findNode(okey);				  // Retry via findNode
				}
				else
				{
					findPredecessor(okey);		   // Clean index
					if(head.right == null)
					{
						tryReduceLevel();
					}
				}
				return (V) v;
			}
		}
	}

	/**
	 * Possibly reduce head level if it has no nodes.  This method can
	 * (rarely) make mistakes, in which case levels can disappear even
	 * though they are about to contain index nodes. This impacts
	 * performance, not correctness.  To minimize mistakes as well as
	 * to reduce hysteresis, the level is reduced by one only if the
	 * topmost three levels look empty. Also, if the removed level
	 * looks non-empty after CAS, we try to change it back quick
	 * before anyone notices our mistake! (This trick works pretty
	 * well because this method will practically never make mistakes
	 * unless current thread stalls immediately before first CAS, in
	 * which case it is very unlikely to stall again immediately
	 * afterwards, so will recover.)
	 * </p>
	 * We put up with all this rather than just let levels grow
	 * because otherwise, even a small map that has undergone a large
	 * number of insertions and removals will have a lot of levels,
	 * slowing down access more than would an occasional unwanted
	 * reduction.
	 */
	private void tryReduceLevel()
	{
		HeadIndex<V> h = head;
		HeadIndex<V> d;
		HeadIndex<V> e;
		if(h.level > 3 && (d = (HeadIndex<V>) h.down) != null && (e = (HeadIndex<V>) d.down) != null && e.right == null && d.right == null && h.right == null && casHead(h, d) && // try to set
				h.right != null) // recheck
		{
			casHead(d, h);   // try to backout
		}
	}

	/* ---------------- Finding and removing first element -------------- */

	/**
	 * Specialized variant of findNode to get first valid node.
	 *
	 * @return first node or null if empty
	 */
	Node<V> findFirst()
	{
		for(; ;)
		{
			Node<V> b = head.node;
			Node<V> n = b.next;
			if(n == null)
			{
				return null;
			}
			if(n.value != null)
			{
				return n;
			}
			n.helpDelete(b, n.next);
		}
	}

	/**
	 * Removes first entry; returns its snapshot.
	 *
	 * @return null if empty, else snapshot of first entry
	 */
	@SuppressWarnings("unchecked")
	IntObjectPair<V> doRemoveFirstEntry()
	{
		for(; ;)
		{
			Node<V> b = head.node;
			Node<V> n = b.next;
			if(n == null)
			{
				return null;
			}
			Node<V> f = n.next;
			if(n != b.next)
			{
				continue;
			}
			Object v = n.value;
			if(v == null)
			{
				n.helpDelete(b, f);
				continue;
			}
			if(!n.casValue(v, null))
			{
				continue;
			}
			if(!n.appendMarker(f) || !b.casNext(n, f))
			{
				findFirst(); // retry
			}
			clearIndexToFirst();
			return new ImmutableIntObjectPairImpl<V>(n.key, (V) v);
		}
	}

	/**
	 * Clears out index nodes associated with deleted first entry.
	 */
	private void clearIndexToFirst()
	{
		for(; ;)
		{
			Index<V> q = head;
			for(; ;)
			{
				Index<V> r = q.right;
				if(r != null && r.indexesDeletedNode() && !q.unlink(r))
				{
					break;
				}
				if((q = q.down) == null)
				{
					if(head.right == null)
					{
						tryReduceLevel();
					}
					return;
				}
			}
		}
	}


	/* ---------------- Finding and removing last element -------------- */

	/**
	 * Specialized version of find to get last valid node.
	 *
	 * @return last node or null if empty
	 */
	Node<V> findLast()
	{
		/*
				 * findPredecessor can't be used to traverse index level
				 * because this doesn't use comparisons.  So traversals of
				 * both levels are folded together.
				 */
		Index<V> q = head;
		for(; ;)
		{
			Index<V> d, r;
			if((r = q.right) != null)
			{
				if(r.indexesDeletedNode())
				{
					q.unlink(r);
					q = head; // restart
				}
				else
				{
					q = r;
				}
			}
			else if((d = q.down) != null)
			{
				q = d;
			}
			else
			{
				Node<V> b = q.node;
				Node<V> n = b.next;
				for(; ;)
				{
					if(n == null)
					{
						return (b.isBaseHeader()) ? null : b;
					}
					Node<V> f = n.next;			// inconsistent read
					if(n != b.next)
					{
						break;
					}
					Object v = n.value;
					if(v == null)
					{				 // n is deleted
						n.helpDelete(b, f);
						break;
					}
					if(v == n || b.value == null)   // b is deleted
					{
						break;
					}
					b = n;
					n = f;
				}
				q = head; // restart
			}
		}
	}

	/**
	 * Specialized variant of findPredecessor to get predecessor of last
	 * valid node.  Needed when removing the last entry.  It is possible
	 * that all successors of returned node will have been deleted upon
	 * return, in which case this method can be retried.
	 *
	 * @return likely predecessor of last node
	 */
	private Node<V> findPredecessorOfLast()
	{
		for(; ;)
		{
			Index<V> q = head;
			for(; ;)
			{
				Index<V> d, r;
				if((r = q.right) != null)
				{
					if(r.indexesDeletedNode())
					{
						q.unlink(r);
						break;	// must restart
					}
					// proceed as far across as possible without overshooting
					if(r.node.next != null)
					{
						q = r;
						continue;
					}
				}
				if((d = q.down) != null)
				{
					q = d;
				}
				else
				{
					return q.node;
				}
			}
		}
	}

	/**
	 * Removes last entry; returns its snapshot.
	 * Specialized variant of doRemove.
	 *
	 * @return null if empty, else snapshot of last entry
	 */
	@SuppressWarnings("unchecked")
	IntObjectPair<V> doRemoveLastEntry()
	{
		for(; ;)
		{
			Node<V> b = findPredecessorOfLast();
			Node<V> n = b.next;
			if(n == null)
			{
				if(b.isBaseHeader())			   // empty
				{
					return null;
				}
				else
				{
					continue; // all b's successors are deleted; retry
				}
			}
			for(; ;)
			{
				Node<V> f = n.next;
				if(n != b.next)					// inconsistent read
				{
					break;
				}
				Object v = n.value;
				if(v == null)
				{					// n is deleted
					n.helpDelete(b, f);
					break;
				}
				if(v == n || b.value == null)	  // b is deleted
				{
					break;
				}
				if(f != null)
				{
					b = n;
					n = f;
					continue;
				}
				if(!n.casValue(v, null))
				{
					break;
				}

				int key = n.key;

				if(!n.appendMarker(f) || !b.casNext(n, f))
				{
					findNode(key);				  // Retry via findNode
				}
				else
				{
					findPredecessor(key);		   // Clean index
					if(head.right == null)
					{
						tryReduceLevel();
					}
				}
				return new ImmutableIntObjectPairImpl<V>(key, (V) v);
			}
		}
	}

	/* ---------------- Relational operations -------------- */

	// Control values OR'ed as arguments to findNear

	private static final int EQ = 1;
	private static final int LT = 2;
	private static final int GT = 0; // Actually checked as !LT

	/**
	 * Utility for ceiling, floor, lower, higher methods.
	 *
	 * @param kkey the key
	 * @param rel  the relation -- OR'ed combination of EQ, LT, GT
	 * @return nearest node fitting relation, or null if no such
	 */
	Node<V> findNear(int kkey, int rel)
	{
		for(; ;)
		{
			Node<V> b = findPredecessor(kkey);
			Node<V> n = b.next;
			for(; ;)
			{
				if(n == null)
				{
					return ((rel & LT) == 0 || b.isBaseHeader()) ? null : b;
				}
				Node<V> f = n.next;
				if(n != b.next)				  // inconsistent read
				{
					break;
				}
				Object v = n.value;
				if(v == null)  // n is deleted
				{
					n.helpDelete(b, f);
					break;
				}
				if(v == n || b.value == null)	// b is deleted
				{
					break;
				}
				int c = compare(kkey, n.key);
				if((c == 0 && (rel & EQ) != 0) || (c < 0 && (rel & LT) == 0))
				{
					return n;
				}
				if(c <= 0 && (rel & LT) != 0)
				{
					return (b.isBaseHeader()) ? null : b;
				}
				b = n;
				n = f;
			}
		}
	}

	/**
	 * Returns SimpleImmutableEntry for results of findNear.
	 *
	 * @param key the key
	 * @param rel the relation -- OR'ed combination of EQ, LT, GT
	 * @return Entry fitting relation, or null if no such
	 */
	IntObjectPair<V> getNear(int key, int rel)
	{
		for(; ;)
		{
			Node<V> n = findNear(key, rel);
			if(n == null)
			{
				return null;
			}
			IntObjectPair<V> e = n.createSnapshot();
			if(e != null)
			{
				return e;
			}
		}
	}


	/* ---------------- Constructors -------------- */

	/**
	 * Constructs a new, empty map, sorted according to the
	 * {@linkplain Comparable natural ordering} of the keys.
	 */
	public CTreeIntObjectMap()
	{
		this.comparator = null;
		initialize();
	}

	/**
	 * Constructs a new, empty map, sorted according to the specified
	 * comparator.
	 *
	 * @param comparator the comparator that will be used to order this map.
	 *                   If null, the {@linkplain Comparable natural
	 *                   ordering} of the keys will be used.
	 */
	public CTreeIntObjectMap(IntComparator comparator)
	{
		this.comparator = comparator;
		initialize();
	}

	/**
	 * Constructs a new map containing the same mappings as the given map,
	 * sorted according to the {@linkplain Comparable natural ordering} of
	 * the keys.
	 *
	 * @param m the map whose mappings are to be placed in this map
	 * @throws ClassCastException   if the keys in m are not
	 *                              {@link Comparable}, or are not mutually comparable
	 * @throws NullPointerException if the specified map or any of its keys
	 *                              or values are null
	 */
	public CTreeIntObjectMap(IntObjectMap<? extends V> m)
	{
		this.comparator = null;
		initialize();
		putAll(m);
	}

	/**
	 * Constructs a new map containing the same mappings and using the
	 * same ordering as the specified sorted map.
	 *
	 * @param m the sorted map whose mappings are to be placed in this
	 *          map, and whose comparator is to be used to sort this map
	 * @throws NullPointerException if the specified sorted map or any of
	 *                              its keys or values are null
	 */
	public CTreeIntObjectMap(SortedIntObjectMap<? extends V> m)
	{
		this.comparator = m.comparator();
		initialize();
		buildFromSorted(m);
	}

	/**
	 * Returns a shallow copy of this CTreeIntObjectMap
	 * instance. (The keys and values themselves are not cloned.)
	 *
	 * @return a shallow copy of this map
	 */
	@SuppressWarnings("unchecked")
	public CTreeIntObjectMap<V> clone()
	{
		CTreeIntObjectMap<V> clone = null;
		try
		{
			clone = (CTreeIntObjectMap<V>) super.clone();
		}
		catch(CloneNotSupportedException e)
		{
			throw new InternalError();
		}

		clone.initialize();
		clone.buildFromSorted(this);
		return clone;
	}

	/**
	 * Streamlined bulk insertion to initialize from elements of
	 * given sorted map.  Call only from constructor or clone
	 * method.
	 */
	private void buildFromSorted(SortedIntObjectMap<? extends V> map)
	{
		if(map == null)
		{
			throw new NullPointerException();
		}

		HeadIndex<V> h = head;
		Node<V> basepred = h.node;

		// Track the current rightmost node at each level. Uses an
		// ArrayList to avoid committing to initial or maximum level.
		ArrayList<Index<V>> preds = new ArrayList<Index<V>>();

		// initialize
		for(int i = 0; i <= h.level; ++i)
		{
			preds.add(null);
		}
		Index<V> q = h;
		for(int i = h.level; i > 0; --i)
		{
			preds.set(i, q);
			q = q.down;
		}

		Iterator<? extends IntObjectPair<? extends V>> it = map.entrySet().iterator();
		while(it.hasNext())
		{
			IntObjectPair<? extends V> e = it.next();
			int j = randomLevel();
			if(j > h.level)
			{
				j = h.level + 1;
			}
			int k = e.getKey();
			V v = e.getValue();
			if(v == null)
			{
				throw new NullPointerException();
			}
			Node<V> z = new Node<V>(k, v, null);
			basepred.next = z;
			basepred = z;
			if(j > 0)
			{
				Index<V> idx = null;
				for(int i = 1; i <= j; ++i)
				{
					idx = new Index<V>(z, idx, null);
					if(i > h.level)
					{
						h = new HeadIndex<V>(h.node, h, idx, i);
					}

					if(i < preds.size())
					{
						preds.get(i).right = idx;
						preds.set(i, idx);
					}
					else
					{
						preds.add(idx);
					}
				}
			}
		}
		head = h;
	}

	/* ---------------- Serialization -------------- */

	/**
	 * Save the state of this map to a stream.
	 *
	 * @serialData The key (Object) and value (Object) for each
	 * key-value mapping represented by the map, followed by
	 * null. The key-value mappings are emitted in key-order
	 * (as determined by the Comparator, or by the keys' natural
	 * ordering if no Comparator).
     *
     * @param s the stream
     * @throws java.io.IOException if the stream throws a exception
	 */
	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException
	{
		// Write out the Comparator and any hidden stuff
		s.defaultWriteObject();

		// Write out keys and values (alternating)
		for(Node<V> n = findFirst(); n != null; n = n.next)
		{
			V v = n.getValidValue();
			if(v != null)
			{
				s.writeObject(n.key);
				s.writeObject(v);
			}
		}
		s.writeObject(null);
	}

	/**
	 * Reconstitute the map from a stream.
     *
     * @param s the stream
     * @throws java.io.IOException if the stream throws a exception
     * @throws  ClassNotFoundException it the stream represent a unknown class
	 */
	private void readObject(final java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException
	{
		// Read in the Comparator and any hidden stuff
		s.defaultReadObject();
		// Reset transients
		initialize();

		/*
				 * This is nearly identical to buildFromSorted, but is
				 * distinct because readObject calls can't be nicely adapted
				 * as the kind of iterator needed by buildFromSorted. (They
				 * can be, but doing so requires type cheats and/or creation
				 * of adaptor classes.) It is simpler to just adapt the code.
				 */

		HeadIndex<V> h = head;
		Node<V> basepred = h.node;
		ArrayList<Index<V>> preds = new ArrayList<Index<V>>();
		for(int i = 0; i <= h.level; ++i)
		{
			preds.add(null);
		}
		Index<V> q = h;
		for(int i = h.level; i > 0; --i)
		{
			preds.set(i, q);
			q = q.down;
		}

		for(; ;)
		{
			int key = 0;
			try
			{
				key = s.readInt();
			}
			catch(IOException e)
			{
				break;
			}
			Object v = s.readObject();
			if(v == null)
			{
				throw new NullPointerException();
			}

			@SuppressWarnings("unchecked")
			V val = (V) v;
			int j = randomLevel();
			if(j > h.level)
			{
				j = h.level + 1;
			}
			Node<V> z = new Node<V>(key, val, null);
			basepred.next = z;
			basepred = z;
			if(j > 0)
			{
				Index<V> idx = null;
				for(int i = 1; i <= j; ++i)
				{
					idx = new Index<V>(z, idx, null);
					if(i > h.level)
					{
						h = new HeadIndex<V>(h.node, h, idx, i);
					}

					if(i < preds.size())
					{
						preds.get(i).right = idx;
						preds.set(i, idx);
					}
					else
					{
						preds.add(idx);
					}
				}
			}
		}
		head = h;
	}

	/* ------ IntObjectMap API methods ------ */

	/**
	 * Returns true if this map contains a mapping for the specified
	 * key.
	 *
	 * @param key key whose presence in this map is to be tested
	 * @return true if this map contains a mapping for the specified key
	 * @throws ClassCastException   if the specified key cannot be compared
	 *                              with the keys currently in the map
	 * @throws NullPointerException if the specified key is null
	 */
	public boolean containsKey(int key)
	{
		return doGet(key) != null;
	}

	/**
	 * <p>
	 * Returns the value to which the specified key is mapped,
	 * or {@code null} if this map contains no mapping for the key.
	 * </p>
	 * <p>More formally, if this map contains a mapping from a key
	 * {@code k} to a value {@code v} such that {@code key} compares
	 * equal to {@code k} according to the map's ordering, then this
	 * method returns {@code v}; otherwise it returns {@code null}.
	 * (There can be at most one such mapping.)
	 *
	 * @throws ClassCastException   if the specified key cannot be compared
	 *                              with the keys currently in the map
	 * @throws NullPointerException if the specified key is null
	 */
	public V get(int key)
	{
		return doGet(key);
	}

	/**
	 * Associates the specified value with the specified key in this map.
	 * If the map previously contained a mapping for the key, the old
	 * value is replaced.
	 *
	 * @param key   key with which the specified value is to be associated
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with the specified key, or
	 *         null if there was no mapping for the key
	 * @throws ClassCastException   if the specified key cannot be compared
	 *                              with the keys currently in the map
	 * @throws NullPointerException if the specified key or value is null
	 */
	public V put(int key, V value)
	{
		if(value == null)
			throw new NullPointerException();
		return doPut(key, value, false);
	}

	/**
	 * Removes the mapping for the specified key from this map if present.
	 *
	 * @param key key for which mapping should be removed
	 * @return the previous value associated with the specified key, or
	 *         null if there was no mapping for the key
	 * @throws ClassCastException   if the specified key cannot be compared
	 *                              with the keys currently in the map
	 * @throws NullPointerException if the specified key is null
	 */
	public V remove(int key)
	{
		return doRemove(key, null);
	}

	/**
	 * Returns true if this map maps one or more keys to the
	 * specified value.  This operation requires time linear in the
	 * map size.
	 *
	 * @param value value whose presence in this map is to be tested
	 * @return true if a mapping to value exists;
	 *         false otherwise
	 * @throws NullPointerException if the specified value is null
	 */
	public boolean containsValue(Object value)
	{
		if(value == null)
		{
			throw new NullPointerException();
		}
		for(Node<V> n = findFirst(); n != null; n = n.next)
		{
			V v = n.getValidValue();
			if(v != null && value.equals(v))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * <p>
	 * Returns the number of key-value mappings in this map.  If this map
	 * contains more than Integer.MAX_VALUE elements, it
	 * returns Integer.MAX_VALUE.
	 * </p>
	 * <p>Beware that, unlike in most collections, this method is
	 * <em>NOT</em> a constant-time operation. Because of the
	 * asynchronous nature of these maps, determining the current
	 * number of elements requires traversing them all to count them.
	 * Additionally, it is possible for the size to change during
	 * execution of this method, in which case the returned result
	 * will be inaccurate. Thus, this method is typically not very
	 * useful in concurrent applications.
	 *
	 * @return the number of elements in this map
	 */
	public int size()
	{
		long count = 0;
		for(Node<V> n = findFirst(); n != null; n = n.next)
		{
			if(n.getValidValue() != null)
			{
				++count;
			}
		}
		return (count >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) count;
	}

	/**
	 * Returns true if this map contains no key-value mappings.
	 *
	 * @return true if this map contains no key-value mappings
	 */
	public boolean isEmpty()
	{
		return findFirst() == null;
	}

	/**
	 * Removes all of the mappings from this map.
	 */
	public void clear()
	{
		initialize();
	}

	/* ---------------- View methods -------------- */

	/*
		 * Note: Lazy initialization works for views because view classes
		 * are stateless/immutable so it doesn't matter wrt correctness if
		 * more than one is created (which will only rarely happen).  Even
		 * so, the following idiom conservatively ensures that the method
		 * returns the one it created if it does so, not one created by
		 * another racing thread.
		 */

	/**
	 * <p>
	 * Returns a {@link NavigableSet} view of the keys contained in this map.
	 * The set's iterator returns the keys in ascending order.
	 * The set is backed by the map, so changes to the map are
	 * reflected in the set, and vice-versa.  The set supports element
	 * removal, which removes the corresponding mapping from the map,
	 * via the {@code Iterator.remove}, {@code Set.remove},
	 * {@code removeAll}, {@code retainAll}, and {@code clear}
	 * operations.  It does not support the {@code add} or {@code addAll}
	 * operations.
	 * </p>
	 * <p>The view's {@code iterator} is a "weakly consistent" iterator
	 * that will never throw {@link ConcurrentModificationException},
	 * and guarantees to traverse elements as they existed upon
	 * construction of the iterator, and may (but is not guaranteed to)
	 * reflect any modifications subsequent to construction.
	 * </p>
	 * <p>This method is equivalent to method {@code navigableKeySet}.
	 *
	 * @return a navigable set view of the keys in this map
	 */
	public NavigableIntSet keySet()
	{
		KeySet ks = keySet;
		return (ks != null) ? ks : (keySet = new KeySet(this));
	}

	public NavigableIntSet navigableKeySet()
	{
		KeySet ks = keySet;
		return (ks != null) ? ks : (keySet = new KeySet(this));
	}

	/**
	 * <p>
	 * Returns a {@link Collection} view of the values contained in this map.
	 * The collection's iterator returns the values in ascending order
	 * of the corresponding keys.
	 * The collection is backed by the map, so changes to the map are
	 * reflected in the collection, and vice-versa.  The collection
	 * supports element removal, which removes the corresponding
	 * mapping from the map, via the Iterator.remove,
	 * Collection.remove, removeAll,
	 * retainAll and clear operations.  It does not
	 * support the add or addAll operations.
	 * </p>
	 * <p>The view's iterator is a "weakly consistent" iterator
	 * that will never throw {@link ConcurrentModificationException},
	 * and guarantees to traverse elements as they existed upon
	 * construction of the iterator, and may (but is not guaranteed to)
	 * reflect any modifications subsequent to construction.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Collection<V> values()
	{
		Values vs = values;
		return (vs != null) ? vs : (values = new Values(this));
	}

	/**
	 * <p>
	 * Returns a {@link Set} view of the mappings contained in this map.
	 * The set's iterator returns the entries in ascending key order.
	 * The set is backed by the map, so changes to the map are
	 * reflected in the set, and vice-versa.  The set supports element
	 * removal, which removes the corresponding mapping from the map,
	 * via the Iterator.remove, Set.remove,
	 * removeAll, retainAll and clear
	 * operations.  It does not support the add or
	 * addAll operations.
	 * </p>
	 * <p>The view's iterator is a "weakly consistent" iterator
	 * that will never throw {@link ConcurrentModificationException},
	 * and guarantees to traverse elements as they existed upon
	 * construction of the iterator, and may (but is not guaranteed to)
	 * reflect any modifications subsequent to construction.
	 * </p>
	 * <p>The IntObjectPair elements returned by
	 * iterator.next() do <em>not</em> support the
	 * setValue operation.
	 *
	 * @return a set view of the mappings contained in this map,
	 *         sorted in ascending key order
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Set<IntObjectPair<V>> entrySet()
	{
		EntrySet es = entrySet;
		return (es != null) ? es : (entrySet = new EntrySet(this));
	}

	public CNavigableIntObjectMap<V> descendingMap()
	{
		CNavigableIntObjectMap<V> dm = descendingMap;
		return (dm != null) ? dm : (descendingMap = new SubMap<V>(this, 0, false, 0, false, true));
	}

	public NavigableIntSet descendingKeySet()
	{
		return descendingMap().navigableKeySet();
	}

	/* ---------------- AbstractMap Overrides -------------- */

	/**
	 * Compares the specified object with this map for equality.
	 * Returns true if the given object is also a map and the
	 * two maps represent the same mappings.  More formally, two maps
	 * m1 and m2 represent the same mappings if
	 * m1.entrySet().equals(m2.entrySet()).  This
	 * operation may return misleading results if either map is
	 * concurrently modified during execution of this method.
	 *
	 * @param o object to be compared for equality with this map
	 * @return true if the specified object is equal to this map
	 */
	public boolean equals(Object o)
	{
		if(o == this)
		{
			return true;
		}
		if(!(o instanceof IntObjectMap))
		{
			return false;
		}
		IntObjectMap<?> m = (IntObjectMap<?>) o;
		try
		{
			for(IntObjectPair<V> e : this.entrySet())
			{
				if(!e.getValue().equals(m.get(e.getKey())))
				{
					return false;
				}
			}
			for(IntObjectPair<?> e : m.entrySet())
			{
				int k = e.getKey();
				Object v = e.getValue();
				if(v == null || !v.equals(get(k)))
				{
					return false;
				}
			}
			return true;
		}
		catch(ClassCastException unused)
		{
			return false;
		}
		catch(NullPointerException unused)
		{
			return false;
		}
	}

	/* ------ ConcurrentMap API methods ------ */

	/**
	 * {@inheritDoc}
	 *
	 * @return the previous value associated with the specified key,
	 *         or null if there was no mapping for the key
	 * @throws ClassCastException   if the specified key cannot be compared
	 *                              with the keys currently in the map
	 * @throws NullPointerException if the specified key or value is null
	 */
	public V putIfAbsent(int key, V value)
	{
		if(value == null)
		{
			throw new NullPointerException();
		}
		return doPut(key, value, true);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws ClassCastException   if the specified key cannot be compared
	 *                              with the keys currently in the map
	 * @throws NullPointerException if the specified key is null
	 */
	public boolean remove(int key, Object value)
	{
		if(value == null)
		{
			return false;
		}
		return doRemove(key, value) != null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws ClassCastException   if the specified key cannot be compared
	 *                              with the keys currently in the map
	 * @throws NullPointerException if any of the arguments are null
	 */
	public boolean replace(int key, V oldValue, V newValue)
	{
		if(oldValue == null || newValue == null)
		{
			throw new NullPointerException();
		}

		for(; ;)
		{
			Node<V> n = findNode(key);
			if(n == null)
			{
				return false;
			}
			Object v = n.value;
			if(v != null)
			{
				if(!oldValue.equals(v))
				{
					return false;
				}
				if(n.casValue(v, newValue))
				{
					return true;
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return the previous value associated with the specified key,
	 *         or null if there was no mapping for the key
	 * @throws ClassCastException   if the specified key cannot be compared
	 *                              with the keys currently in the map
	 * @throws NullPointerException if the specified key or value is null
	 */
	@SuppressWarnings("unchecked")
	public V replace(int key, V value)
	{
		if(value == null)
		{
			throw new NullPointerException();
		}

		for(; ;)
		{
			Node<V> n = findNode(key);
			if(n == null)
			{
				return null;
			}
			Object v = n.value;
			if(v != null && n.casValue(v, value))
			{
				return (V) v;
			}
		}
	}

	/* ------ SortedMap API methods ------ */

	public IntComparator comparator()
	{
		return comparator;
	}

	/**
	 * @throws NoSuchElementException {@inheritDoc}
	 */
	public int firstKey()
	{
		Node<V> n = findFirst();
		if(n == null)
		{
			throw new NoSuchElementException();
		}
		return n.key;
	}

	/**
	 * @throws NoSuchElementException {@inheritDoc}
	 */
	public int lastKey()
	{
		Node<V> n = findLast();
		if(n == null)
		{
			throw new NoSuchElementException();
		}
		return n.key;
	}

	/**
	 * @throws ClassCastException	   {@inheritDoc}
	 * @throws NullPointerException	 if {@code fromKey} or {@code toKey} is null
	 * @throws IllegalArgumentException {@inheritDoc}
	 */
	public CNavigableIntObjectMap<V> subMap(int fromKey, boolean fromInclusive, int toKey, boolean toInclusive)
	{
		return new SubMap<V>(this, fromKey, fromInclusive, toKey, toInclusive, false);
	}

	/**
	 * @throws ClassCastException	   {@inheritDoc}
	 * @throws NullPointerException	 if {@code toKey} is null
	 * @throws IllegalArgumentException {@inheritDoc}
	 */
	public CNavigableIntObjectMap<V> headMap(int toKey, boolean inclusive)
	{
		return new SubMap<V>(this, 0, false, toKey, inclusive, false);
	}

	/**
	 * @throws ClassCastException	   {@inheritDoc}
	 * @throws NullPointerException	 if {@code fromKey} is null
	 * @throws IllegalArgumentException {@inheritDoc}
	 */
	public CNavigableIntObjectMap<V> tailMap(int fromKey, boolean inclusive)
	{
		return new SubMap<V>(this, fromKey, inclusive, 0, false, false);
	}

	/**
	 * @throws ClassCastException	   {@inheritDoc}
	 * @throws NullPointerException	 if {@code fromKey} or {@code toKey} is null
	 * @throws IllegalArgumentException {@inheritDoc}
	 */
	public CNavigableIntObjectMap<V> subMap(int fromKey, int toKey)
	{
		return subMap(fromKey, true, toKey, false);
	}

	/**
	 * @throws ClassCastException	   {@inheritDoc}
	 * @throws NullPointerException	 if {@code toKey} is null
	 * @throws IllegalArgumentException {@inheritDoc}
	 */
	public CNavigableIntObjectMap<V> headMap(int toKey)
	{
		return headMap(toKey, false);
	}

	/**
	 * @throws ClassCastException	   {@inheritDoc}
	 * @throws NullPointerException	 if {@code fromKey} is null
	 * @throws IllegalArgumentException {@inheritDoc}
	 */
	public CNavigableIntObjectMap<V> tailMap(int fromKey)
	{
		return tailMap(fromKey, true);
	}

	/* ---------------- Relational operations -------------- */

	/**
	 * Returns a key-value mapping associated with the greatest key
	 * strictly less than the given key, or null if there is
	 * no such key. The returned entry does <em>not</em> support the
	 * Entry.setValue method.
	 *
	 * @throws ClassCastException   {@inheritDoc}
	 * @throws NullPointerException if the specified key is null
	 */
	public IntObjectPair<V> lowerEntry(int key)
	{
		return getNear(key, LT);
	}

	/**
	 * @throws ClassCastException   {@inheritDoc}
	 * @throws NullPointerException if the specified key is null
	 */
	public int lowerKey(int key)
	{
		Node<V> n = findNear(key, LT);
		return (n == null) ? null : n.key;
	}

	/**
	 * Returns a key-value mapping associated with the greatest key
	 * less than or equal to the given key, or null if there
	 * is no such key. The returned entry does <em>not</em> support
	 * the Entry.setValue method.
	 *
	 * @param key the key
	 * @throws ClassCastException   {@inheritDoc}
	 * @throws NullPointerException if the specified key is null
	 */
	public IntObjectPair<V> floorEntry(int key)
	{
		return getNear(key, LT | EQ);
	}

	/**
	 * @param key the key
	 * @throws ClassCastException   {@inheritDoc}
	 * @throws NullPointerException if the specified key is null
	 */
	public int floorKey(int key)
	{
		Node<V> n = findNear(key, LT | EQ);
		return (n == null) ? null : n.key;
	}

	/**
	 * Returns a key-value mapping associated with the least key
	 * greater than or equal to the given key, or null if
	 * there is no such entry. The returned entry does <em>not</em>
	 * support the Entry.setValue method.
	 *
	 * @throws ClassCastException   {@inheritDoc}
	 * @throws NullPointerException if the specified key is null
	 */
	public IntObjectPair<V> ceilingEntry(int key)
	{
		return getNear(key, GT | EQ);
	}

	/**
	 * @throws ClassCastException   {@inheritDoc}
	 * @throws NullPointerException if the specified key is null
	 */
	public int ceilingKey(int key)
	{
		Node<V> n = findNear(key, GT | EQ);
		return (n == null) ? null : n.key;
	}

	/**
	 * Returns a key-value mapping associated with the least key
	 * strictly greater than the given key, or null if there
	 * is no such key. The returned entry does <em>not</em> support
	 * the Entry.setValue method.
	 *
	 * @param key the key
	 * @throws ClassCastException   {@inheritDoc}
	 * @throws NullPointerException if the specified key is null
	 */
	public IntObjectPair<V> higherEntry(int key)
	{
		return getNear(key, GT);
	}

	/**
	 * @param key the key
	 * @throws ClassCastException   {@inheritDoc}
	 * @throws NullPointerException if the specified key is null
	 */
	public int higherKey(int key)
	{
		Node<V> n = findNear(key, GT);
		return (n == null) ? null : n.key;
	}

	/**
	 * Returns a key-value mapping associated with the least
	 * key in this map, or null if the map is empty.
	 * The returned entry does <em>not</em> support
	 * the Entry.setValue method.
	 */
	public IntObjectPair<V> firstEntry()
	{
		for(; ;)
		{
			Node<V> n = findFirst();
			if(n == null)
			{
				return null;
			}
			IntObjectPair<V> e = n.createSnapshot();
			if(e != null)
			{
				return e;
			}
		}
	}

	/**
	 * Returns a key-value mapping associated with the greatest
	 * key in this map, or null if the map is empty.
	 * The returned entry does <em>not</em> support
	 * the Entry.setValue method.
	 */
	public IntObjectPair<V> lastEntry()
	{
		for(; ;)
		{
			Node<V> n = findLast();
			if(n == null)
			{
				return null;
			}
			IntObjectPair<V> e = n.createSnapshot();
			if(e != null)
			{
				return e;
			}
		}
	}

	/**
	 * Removes and returns a key-value mapping associated with
	 * the least key in this map, or null if the map is empty.
	 * The returned entry does <em>not</em> support
	 * the Entry.setValue method.
	 */
	public IntObjectPair<V> pollFirstEntry()
	{
		return doRemoveFirstEntry();
	}

	/**
	 * Removes and returns a key-value mapping associated with
	 * the greatest key in this map, or null if the map is empty.
	 * The returned entry does <em>not</em> support
	 * the Entry.setValue method.
	 */
	public IntObjectPair<V> pollLastEntry()
	{
		return doRemoveLastEntry();
	}


	/* ---------------- Iterators -------------- */

	/**
	 * Base of iterator classes:
	 */
	abstract class Iter<T>
	{
		/**
		 * the last node returned by next()
		 */
		Node<V> lastReturned;
		/**
		 * the next node to return from next();
		 */
		Node<V> next;
		/**
		 * Cache of next value field to maintain weak consistency
		 */
		V nextValue;

		/**
		 * Initializes ascending iterator for entire range.
		 */
		@SuppressWarnings("unchecked")
		Iter()
		{
			for(; ;)
			{
				next = findFirst();
				if(next == null)
				{
					break;
				}
				Object x = next.value;
				if(x != null && x != next)
				{
					nextValue = (V) x;
					break;
				}
			}
		}

		public final boolean hasNext()
		{
			return next != null;
		}

		/**
		 * Advances next to higher entry.
		 */
		@SuppressWarnings("unchecked")
		final void advance()
		{
			if(next == null)
			{
				throw new NoSuchElementException();
			}
			lastReturned = next;
			for(; ;)
			{
				next = next.next;
				if(next == null)
				{
					break;
				}
				Object x = next.value;
				if(x != null && x != next)
				{
					nextValue = (V) x;
					break;
				}
			}
		}

		public void remove()
		{
			Node<V> l = lastReturned;
			if(l == null)
			{
				throw new IllegalStateException();
			}
			// It would not be worth all of the overhead to directly
			// unlink from here. Using remove is fast enough.
			CTreeIntObjectMap.this.remove(l.key);
			lastReturned = null;
		}

	}

	final class ValueIterator extends Iter<V> implements Iterator<V>
	{
		public V next()
		{
			V v = nextValue;
			advance();
			return v;
		}
	}

	final class KeyIterator extends Iter<V> implements IntIterator
	{
		public int nextInt()
		{
			Node<V> n = next;
			advance();
			return n.key;
		}
	}

	final class EntryIterator extends Iter<IntObjectPair<V>> implements Iterator<IntObjectPair<V>>
	{
		public IntObjectPair<V> next()
		{
			Node<V> n = next;
			V v = nextValue;
			advance();
			return new ImmutableIntObjectPairImpl<V>(n.key, v);
		}
	}

	// Factory methods for iterators needed by ConcurrentSkipListSet etc

	IntIterator keyIterator()
	{
		return new KeyIterator();
	}

	Iterator<V> valueIterator()
	{
		return new ValueIterator();
	}

	Iterator<IntObjectPair<V>> entryIterator()
	{
		return new EntryIterator();
	}

	/* ---------------- View Classes -------------- */

	/*
		 * View classes are static, delegating to a CNavigableIntObjectMap
		 * to allow use by SubMaps, which outweighs the ugliness of
		 * needing type-tests for Iterator methods.
		 */

	static final <E> List<E> toList(Collection<E> c)
	{
		// Using size() here would be a pessimization.
		List<E> list = new ArrayList<E>(c.size());
		for(E e : c)
			list.add(e);

		return list;
	}

	static final class KeySet extends AbstractIntSet implements NavigableIntSet
	{
		private final CNavigableIntObjectMap<Object> m;

		@SuppressWarnings("unchecked")
		KeySet(CNavigableIntObjectMap<?> map)
		{
			m = (CNavigableIntObjectMap<Object>) map;
		}

		public int size()
		{
			return m.size();
		}

		public boolean isEmpty()
		{
			return m.isEmpty();
		}

		public boolean contains(int o)
		{
			return m.containsKey(o);
		}

		public boolean remove(int o)
		{
			return m.remove(o) != null;
		}

		public void clear()
		{
			m.clear();
		}

		public int lower(int e)
		{
			return m.lowerKey(e);
		}

		public int floor(int e)
		{
			return m.floorKey(e);
		}

		public int ceiling(int e)
		{
			return m.ceilingKey(e);
		}

		public int higher(int e)
		{
			return m.higherKey(e);
		}

		public IntComparator comparator()
		{
			return m.comparator();
		}

		public int first()
		{
			return m.firstKey();
		}

		public int last()
		{
			return m.lastKey();
		}

		public int pollFirst()
		{
			IntObjectPair<Object> e = m.pollFirstEntry();
			return e == null ? null : e.getKey();
		}

		public int pollLast()
		{
			IntObjectPair<Object> e = m.pollLastEntry();
			return e == null ? null : e.getKey();
		}

		public IntIterator iterator()
		{
			if(m instanceof CTreeIntObjectMap)
			{
				return ((CTreeIntObjectMap<Object>) m).keyIterator();
			}
			else
			{
				return ((CTreeIntObjectMap.SubMap<Object>) m).keyIterator();
			}
		}



		public boolean equals(Object o)
		{
			if(o == this)
			{
				return true;
			}
			if(!(o instanceof IntSet))
			{
				return false;
			}
			IntCollection c = (IntCollection) o;
			try
			{
				return containsAll(c) && c.containsAll(this);
			}
			catch(ClassCastException unused)
			{
				return false;
			}
			catch(NullPointerException unused)
			{
				return false;
			}
		}

		public IntIterator descendingIterator()
		{
			return descendingSet().iterator();
		}

		public NavigableIntSet subSet(int fromElement, boolean fromInclusive, int toElement, boolean toInclusive)
		{
			return new CTreeIntSet(m.subMap(fromElement, fromInclusive, toElement, toInclusive));
		}

		public NavigableIntSet headSet(int toElement, boolean inclusive)
		{
			return new CTreeIntSet(m.headMap(toElement, inclusive));
		}

		public NavigableIntSet tailSet(int fromElement, boolean inclusive)
		{
			return new CTreeIntSet(m.tailMap(fromElement, inclusive));
		}

		public NavigableIntSet subSet(int fromElement, int toElement)
		{
			return subSet(fromElement, true, toElement, false);
		}

		public NavigableIntSet headSet(int toElement)
		{
			return headSet(toElement, false);
		}

		public NavigableIntSet tailSet(int fromElement)
		{
			return tailSet(fromElement, true);
		}

		public NavigableIntSet descendingSet()
		{
			return new CTreeIntSet(m.descendingMap());
		}
	}

	static final class Values<E> extends AbstractCollection<E>
	{
		private final CNavigableIntObjectMap<E> m;

		Values(CNavigableIntObjectMap<E> map)
		{
			m = map;
		}

		public Iterator<E> iterator()
		{
			if(m instanceof CTreeIntObjectMap)
			{
				return ((CTreeIntObjectMap<E>) m).valueIterator();
			}
			else
			{
				return ((SubMap<E>) m).valueIterator();
			}
		}

		public boolean isEmpty()
		{
			return m.isEmpty();
		}

		public int size()
		{
			return m.size();
		}

		public boolean contains(Object o)
		{
			return m.containsValue(o);
		}

		public void clear()
		{
			m.clear();
		}

		public Object[] toArray()
		{
			return toList(this).toArray();
		}

		public <T> T[] toArray(T[] a)
		{
			return toList(this).toArray(a);
		}
	}

	static final class EntrySet<V1> extends AbstractSet<IntObjectPair<V1>>
	{
		private final CNavigableIntObjectMap<V1> m;

		EntrySet(CNavigableIntObjectMap<V1> map)
		{
			m = map;
		}

		public Iterator<IntObjectPair<V1>> iterator()
		{
			if(m instanceof CTreeIntObjectMap)
			{
				return ((CTreeIntObjectMap<V1>) m).entryIterator();
			}
			else
			{
				return ((SubMap<V1>) m).entryIterator();
			}
		}

		public boolean contains(Object o)
		{
			if(!(o instanceof IntObjectPair))
			{
				return false;
			}
			@SuppressWarnings("unchecked")
			IntObjectPair<V1> e = (IntObjectPair<V1>) o;
			V1 v = m.get(e.getKey());
			return v != null && v.equals(e.getValue());
		}

		public boolean remove(Object o)
		{
			if(!(o instanceof IntObjectPair))
			{
				return false;
			}
			@SuppressWarnings("unchecked")
			IntObjectPair<V1> e = (IntObjectPair<V1>) o;
			return m.remove(e.getKey(), e.getValue());
		}

		public boolean isEmpty()
		{
			return m.isEmpty();
		}

		public int size()
		{
			return m.size();
		}

		public void clear()
		{
			m.clear();
		}

		public boolean equals(Object o)
		{
			if(o == this)
			{
				return true;
			}
			if(!(o instanceof Set))
			{
				return false;
			}
			Collection<?> c = (Collection<?>) o;
			try
			{
				return containsAll(c) && c.containsAll(this);
			}
			catch(ClassCastException unused)
			{
				return false;
			}
			catch(NullPointerException unused)
			{
				return false;
			}
		}

		public Object[] toArray()
		{
			return toList(this).toArray();
		}

		public <T> T[] toArray(T[] a)
		{
			return toList(this).toArray(a);
		}
	}

	/**
	 * Submaps returned by {@link CTreeIntObjectMap} submap operations
	 * represent a subrange of mappings of their underlying
	 * maps. Instances of this class support all methods of their
	 * underlying maps, differing in that mappings outside their range are
	 * ignored, and attempts to add mappings outside their ranges result
	 * in {@link IllegalArgumentException}.  Instances of this class are
	 * constructed only using the subMap, headMap, and
	 * tailMap methods of their underlying maps.
	 *
	 * @serial include
	 */
	static final class SubMap<V> extends AbstractIntObjectMap<V> implements CNavigableIntObjectMap<V>, Cloneable, java.io.Serializable
	{
		private static final long serialVersionUID = -7647078645895051609L;

		/**
		 * Underlying map
		 */
		private final CTreeIntObjectMap<V> m;
		/**
		 * lower bound key, or null if from start
		 */
		private final int lo;
		/**
		 * upper bound key, or null if to end
		 */
		private final int hi;
		/**
		 * inclusion flag for lo
		 */
		private final boolean loInclusive;
		/**
		 * inclusion flag for hi
		 */
		private final boolean hiInclusive;
		/**
		 * direction
		 */
		private final boolean isDescending;

		// Lazily initialized view holders
		private transient KeySet keySetView;
		private transient Set<IntObjectPair<V>> entrySetView;
		private transient Collection<V> valuesView;

		/**
		 * Creates a new submap, initializing all fields
		 */
		SubMap(CTreeIntObjectMap<V> map, int fromKey, boolean fromInclusive, int toKey, boolean toInclusive, boolean isDescending)
		{
			if(map.compare(fromKey, toKey) > 0)
			{
				throw new IllegalArgumentException("inconsistent range");
			}
			this.m = map;
			this.lo = fromKey;
			this.hi = toKey;
			this.loInclusive = fromInclusive;
			this.hiInclusive = toInclusive;
			this.isDescending = isDescending;
		}

		/* ----------------  Utilities -------------- */

		private boolean tooLow(int key)
		{
			int c = m.compare(key, lo);
			if(c < 0 || (c == 0 && !loInclusive))
			{
				return true;
			}

			return false;
		}

		private boolean tooHigh(int key)
		{
			int c = m.compare(key, hi);
			if(c > 0 || (c == 0 && !hiInclusive))
			{
				return true;
			}
			return false;
		}

		private boolean inBounds(int key)
		{
			return !tooLow(key) && !tooHigh(key);
		}

		private void checkKeyBounds(int key) throws IllegalArgumentException
		{
			if(!inBounds(key))
			{
				throw new IllegalArgumentException("key out of range");
			}
		}

		/**
		 * Returns true if node key is less than upper bound of range
		 */
		private boolean isBeforeEnd(CTreeIntObjectMap.Node<V> n)
		{
			if(n == null)
			{
				return false;
			}
			/*if(hi == 0)
			{
				return true;
			} */
			int k = n.key;
			/*if(k == 0) // pass by markers and headers
			{
				return true;
			} */
			int c = m.compare(k, hi);
			if(c > 0 || (c == 0 && !hiInclusive))
			{
				return false;
			}
			return true;
		}

		/**
		 * Returns lowest node. This node might not be in range, so
		 * most usages need to check bounds
		 */
		private CTreeIntObjectMap.Node<V> loNode()
		{
			/*if(lo == 0)
			{
				return m.findFirst();
			}
			else */
			if(loInclusive)
			{
				return m.findNear(lo, GT | EQ);
			}
			else
			{
				return m.findNear(lo, GT);
			}
		}

		/**
		 * Returns highest node. This node might not be in range, so
		 * most usages need to check bounds
		 */
		private CTreeIntObjectMap.Node<V> hiNode()
		{
			/*if(hi == null)
			{
				return m.findLast();
			}
			else */
			if(hiInclusive)
			{
				return m.findNear(hi, LT | EQ);
			}
			else
			{
				return m.findNear(hi, LT);
			}
		}

		/**
		 * Returns lowest absolute key (ignoring directonality)
		 */
		private int lowestKey()
		{
			CTreeIntObjectMap.Node<V> n = loNode();
			if(isBeforeEnd(n))
			{
				return n.key;
			}
			else
			{
				throw new NoSuchElementException();
			}
		}

		/**
		 * Returns highest absolute key (ignoring directonality)
		 */
		private int highestKey()
		{
			CTreeIntObjectMap.Node<V> n = hiNode();
			if(n != null)
			{
				int last = n.key;
				if(inBounds(last))
				{
					return last;
				}
			}
			throw new NoSuchElementException();
		}

		private IntObjectPair<V> lowestEntry()
		{
			for(; ;)
			{
				CTreeIntObjectMap.Node<V> n = loNode();
				if(!isBeforeEnd(n))
				{
					return null;
				}
				IntObjectPair<V> e = n.createSnapshot();
				if(e != null)
				{
					return e;
				}
			}
		}

		private IntObjectPair<V> highestEntry()
		{
			for(; ;)
			{
				CTreeIntObjectMap.Node<V> n = hiNode();
				if(n == null || !inBounds(n.key))
				{
					return null;
				}
				IntObjectPair<V> e = n.createSnapshot();
				if(e != null)
				{
					return e;
				}
			}
		}

		private IntObjectPair<V> removeLowest()
		{
			for(; ;)
			{
				Node<V> n = loNode();
				if(n == null)
				{
					return null;
				}
				int k = n.key;
				if(!inBounds(k))
				{
					return null;
				}
				V v = m.doRemove(k, null);
				if(v != null)
				{
					return new ImmutableIntObjectPairImpl<V>(k, v);
				}
			}
		}

		private IntObjectPair<V> removeHighest()
		{
			for(; ;)
			{
				Node<V> n = hiNode();
				if(n == null)
				{
					return null;
				}
				int k = n.key;
				if(!inBounds(k))
				{
					return null;
				}
				V v = m.doRemove(k, null);
				if(v != null)
				{
					return new ImmutableIntObjectPairImpl<V>(k, v);
				}
			}
		}

		/**
		 * Submap version of CTreeIntObjectMap.getNearEntry
		 */
		private IntObjectPair<V> getNearEntry(int key, int rel)
		{
			if(isDescending)
			{ // adjust relation for direction
				if((rel & LT) == 0)
				{
					rel |= LT;
				}
				else
				{
					rel &= ~LT;
				}
			}
			if(tooLow(key))
			{
				return ((rel & LT) != 0) ? null : lowestEntry();
			}
			if(tooHigh(key))
			{
				return ((rel & LT) != 0) ? highestEntry() : null;
			}
			for(; ;)
			{
				Node<V> n = m.findNear(key, rel);
				if(n == null || !inBounds(n.key))
				{
					return null;
				}
				int k = n.key;
				V v = n.getValidValue();
				if(v != null)
				{
					return new ImmutableIntObjectPairImpl<V>(k, v);
				}
			}
		}

		// Almost the same as getNearEntry, except for keys
		private int getNearKey(int key, int rel)
		{
			if(isDescending)
			{ // adjust relation for direction
				if((rel & LT) == 0)
				{
					rel |= LT;
				}
				else
				{
					rel &= ~LT;
				}
			}
			if(tooLow(key))
			{
				if((rel & LT) == 0)
				{
					CTreeIntObjectMap.Node<V> n = loNode();
					if(isBeforeEnd(n))
					{
						return n.key;
					}
				}
				return 0;
			}
			if(tooHigh(key))
			{
				if((rel & LT) != 0)
				{
					CTreeIntObjectMap.Node<V> n = hiNode();
					if(n != null)
					{
						int last = n.key;
						if(inBounds(last))
						{
							return last;
						}
					}
				}
				return 0;
			}
			for(; ;)
			{
				Node<V> n = m.findNear(key, rel);
				if(n == null || !inBounds(n.key))
				{
					return 0;
				}
				int k = n.key;
				V v = n.getValidValue();
				if(v != null)
				{
					return k;
				}
			}
		}

		/* ----------------  IntObjectMap API methods -------------- */

		public boolean containsKey(int key)
		{
			int k = key;
			return inBounds(k) && m.containsKey(k);
		}

		public V get(int key)
		{
			int k = key;
			return ((!inBounds(k)) ? null : m.get(k));
		}

		public V put(int key, V value)
		{
			checkKeyBounds(key);
			return m.put(key, value);
		}

		public V remove(int key)
		{
			return (!inBounds(key)) ? null : m.remove(key);
		}

		public int size()
		{
			long count = 0;
			for(CTreeIntObjectMap.Node<V> n = loNode(); isBeforeEnd(n); n = n.next)
			{
				if(n.getValidValue() != null)
				{
					++count;
				}
			}
			return count >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
		}

		public boolean isEmpty()
		{
			return !isBeforeEnd(loNode());
		}

		public boolean containsValue(Object value)
		{
			if(value == null)
			{
				throw new NullPointerException();
			}
			for(CTreeIntObjectMap.Node<V> n = loNode(); isBeforeEnd(n); n = n.next)
			{
				V v = n.getValidValue();
				if(v != null && value.equals(v))
				{
					return true;
				}
			}
			return false;
		}

		public void clear()
		{
			for(CTreeIntObjectMap.Node<V> n = loNode(); isBeforeEnd(n); n = n.next)
			{
				if(n.getValidValue() != null)
				{
					m.remove(n.key);
				}
			}
		}

		/* ----------------  ConcurrentMap API methods -------------- */

		public V putIfAbsent(int key, V value)
		{
			checkKeyBounds(key);
			return m.putIfAbsent(key, value);
		}

		public boolean remove(int key, Object value)
		{
			int k =  key;
			return inBounds(k) && m.remove(k, value);
		}

		public boolean replace(int key, V oldValue, V newValue)
		{
			checkKeyBounds(key);
			return m.replace(key, oldValue, newValue);
		}

		public V replace(int key, V value)
		{
			checkKeyBounds(key);
			return m.replace(key, value);
		}

		/* ----------------  SortedMap API methods -------------- */

		public IntComparator comparator()
		{
			IntComparator cmp = m.comparator();
			if(isDescending)
			{
				return Comparators.reverseOrder(cmp);
			}
			else
			{
				return cmp;
			}
		}

		/**
		 * Utility to create submaps, where given bounds override
		 * unbounded(null) ones and/or are checked against bounded ones.
		 */
		private SubMap<V> newSubMap(int fromKey, boolean fromInclusive, int toKey, boolean toInclusive)
		{
			if(isDescending)
			{ // flip senses
				int tk = fromKey;
				fromKey = toKey;
				toKey = tk;
				boolean ti = fromInclusive;
				fromInclusive = toInclusive;
				toInclusive = ti;
			}
			//if(lo != null)
			{
				/*if(fromKey == null)
				{
					fromKey = lo;
					fromInclusive = loInclusive;
				}
				else */
				{
					int c = m.compare(fromKey, lo);
					if(c < 0 || (c == 0 && !loInclusive && fromInclusive))
					{
						throw new IllegalArgumentException("key out of range");
					}
				}
			}
			//if(hi != null)
			{
				/*if(toKey == null)
				{
					toKey = hi;
					toInclusive = hiInclusive;
				}
				else*/
				{
					int c = m.compare(toKey, hi);
					if(c > 0 || (c == 0 && !hiInclusive && toInclusive))
					{
						throw new IllegalArgumentException("key out of range");
					}
				}
			}
			return new SubMap<V>(m, fromKey, fromInclusive, toKey, toInclusive, isDescending);
		}

		public SubMap<V> subMap(int fromKey, boolean fromInclusive, int toKey, boolean toInclusive)
		{
			return newSubMap(fromKey, fromInclusive, toKey, toInclusive);
		}

		public SubMap<V> headMap(int toKey, boolean inclusive)
		{
			return newSubMap(0, false, toKey, inclusive);
		}

		public SubMap<V> tailMap(int fromKey, boolean inclusive)
		{
			return newSubMap(fromKey, inclusive, 0, false);
		}

		public SubMap<V> subMap(int fromKey, int toKey)
		{
			return subMap(fromKey, true, toKey, false);
		}

		public SubMap<V> headMap(int toKey)
		{
			return headMap(toKey, false);
		}

		public SubMap<V> tailMap(int fromKey)
		{
			return tailMap(fromKey, true);
		}

		public SubMap<V> descendingMap()
		{
			return new SubMap<V>(m, lo, loInclusive, hi, hiInclusive, !isDescending);
		}

		/* ----------------  Relational methods -------------- */

		public IntObjectPair<V> ceilingEntry(int key)
		{
			return getNearEntry(key, (GT | EQ));
		}

		public int ceilingKey(int key)
		{
			return getNearKey(key, (GT | EQ));
		}

		public IntObjectPair<V> lowerEntry(int key)
		{
			return getNearEntry(key, (LT));
		}

		public int lowerKey(int key)
		{
			return getNearKey(key, (LT));
		}

		public IntObjectPair<V> floorEntry(int key)
		{
			return getNearEntry(key, (LT | EQ));
		}

		public int floorKey(int key)
		{
			return getNearKey(key, (LT | EQ));
		}

		public IntObjectPair<V> higherEntry(int key)
		{
			return getNearEntry(key, (GT));
		}

		public int higherKey(int key)
		{
			return getNearKey(key, (GT));
		}

		public int firstKey()
		{
			return isDescending ? highestKey() : lowestKey();
		}

		public int lastKey()
		{
			return isDescending ? lowestKey() : highestKey();
		}

		public IntObjectPair<V> firstEntry()
		{
			return isDescending ? highestEntry() : lowestEntry();
		}

		public IntObjectPair<V> lastEntry()
		{
			return isDescending ? lowestEntry() : highestEntry();
		}

		public IntObjectPair<V> pollFirstEntry()
		{
			return isDescending ? removeHighest() : removeLowest();
		}

		public IntObjectPair<V> pollLastEntry()
		{
			return isDescending ? removeLowest() : removeHighest();
		}

		/* ---------------- Submap Views -------------- */

		public NavigableIntSet keySet()
		{
			KeySet ks = keySetView;
			return (ks != null) ? ks : (keySetView = new KeySet(this));
		}

		public NavigableIntSet navigableKeySet()
		{
			KeySet ks = keySetView;
			return (ks != null) ? ks : (keySetView = new KeySet(this));
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public Collection<V> values()
		{
			Collection<V> vs = valuesView;
			return (vs != null) ? vs : (valuesView = new Values(this));
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public Set<IntObjectPair<V>> entrySet()
		{
			Set<IntObjectPair<V>> es = entrySetView;
			return (es != null) ? es : (entrySetView = new EntrySet(this));
		}

		public NavigableIntSet descendingKeySet()
		{
			return descendingMap().navigableKeySet();
		}

		IntIterator keyIterator()
		{
			return new SubMapKeyIterator();
		}

		Iterator<V> valueIterator()
		{
			return new SubMapValueIterator();
		}

		Iterator<IntObjectPair<V>> entryIterator()
		{
			return new SubMapEntryIterator();
		}

		/**
		 * Variant of main Iter class to traverse through submaps.
		 */
		abstract class SubMapIter<T>
		{
			/**
			 * the last node returned by next()
			 */
			Node<V> lastReturned;
			/**
			 * the next node to return from next();
			 */
			Node<V> next;
			/**
			 * Cache of next value field to maintain weak consistency
			 */
			V nextValue;

			@SuppressWarnings("unchecked")
			SubMapIter()
			{
				for(; ;)
				{
					next = isDescending ? hiNode() : loNode();
					if(next == null)
					{
						break;
					}
					Object x = next.value;
					if(x != null && x != next)
					{
						if(!inBounds(next.key))
						{
							next = null;
						}
						else
						{
							nextValue = (V) x;
						}
						break;
					}
				}
			}

			public final boolean hasNext()
			{
				return next != null;
			}

			final void advance()
			{
				if(next == null)
				{
					throw new NoSuchElementException();
				}
				lastReturned = next;
				if(isDescending)
				{
					descend();
				}
				else
				{
					ascend();
				}
			}

			@SuppressWarnings("unchecked")
			private void ascend()
			{
				for(; ;)
				{
					next = next.next;
					if(next == null)
					{
						break;
					}
					Object x = next.value;
					if(x != null && x != next)
					{
						if(tooHigh(next.key))
						{
							next = null;
						}
						else
						{
							nextValue = (V) x;
						}
						break;
					}
				}
			}

			@SuppressWarnings("unchecked")
			private void descend()
			{
				for(; ;)
				{
					next = m.findNear(lastReturned.key, LT);
					if(next == null)
					{
						break;
					}
					Object x = next.value;
					if(x != null && x != next)
					{
						if(tooLow(next.key))
						{
							next = null;
						}
						else
						{
							nextValue = (V) x;
						}
						break;
					}
				}
			}

			public void remove()
			{
				Node<V> l = lastReturned;
				if(l == null)
				{
					throw new IllegalStateException();
				}
				m.remove(l.key);
				lastReturned = null;
			}

		}

		final class SubMapValueIterator extends SubMapIter<V> implements Iterator<V>
		{
			public V next()
			{
				V v = nextValue;
				advance();
				return v;
			}
		}

		@SuppressWarnings("rawtypes")
		final class SubMapKeyIterator extends SubMapIter implements IntIterator
		{
			public int nextInt()
			{
				@SuppressWarnings("unchecked")
				Node<V> n = next;
				advance();
				return n.key;
			}
		}

		final class SubMapEntryIterator extends SubMapIter<IntObjectPair<V>> implements Iterator<IntObjectPair<V>>
		{
			public IntObjectPair<V> next()
			{
				Node<V> n = next;
				V v = nextValue;
				advance();
				return new ImmutableIntObjectPairImpl<V>(n.key, v);
			}
		}
	}
}
