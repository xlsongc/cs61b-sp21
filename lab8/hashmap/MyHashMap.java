package hashmap;

import java.util.*;
import java.lang.Math;
/**
 *  A hash table-backed Map implementation. Provides amortized constant time
 *  access to elements via get(), remove(), and put() in the best case.
 *
 *  Assumes null keys will never be inserted, and does not resize down upon remove().
 *  @author YOUR NAME HERE
 */
public class MyHashMap<K, V> implements Map61B<K, V> {

    /**
     * Protected helper class to store key/value pairs
     * The protected qualifier allows subclass access
     */
    protected class Node {
        K key;
        V value;

        Node(K k, V v) {
            key = k;
            value = v;
        }
    }

    /* Instance Variables */
    private Collection<Node>[] buckets;
    private double loadFactor;
    // You should probably define some more!
    int size;
    static final int DEFAULT_INITIAL_SIZE = 16;
    static final double DEFAULT_INITIAL_MAXLOAD = 0.75;


    /** Constructors */
    public MyHashMap() {
        this(DEFAULT_INITIAL_SIZE, DEFAULT_INITIAL_MAXLOAD);
    }


    public MyHashMap(int initialSize) {
        this(initialSize, DEFAULT_INITIAL_MAXLOAD);
    }

    /**
     * MyHashMap constructor that creates a backing array of initialSize.
     * The load factor (# items / # buckets) should always be <= loadFactor
     *
     * @param initialSize initial size of backing array
     * @param maxLoad maximum load factor
     */
    public MyHashMap(int initialSize, double maxLoad) {
        this.buckets = createTable(initialSize);
        this.loadFactor = maxLoad;
    }

    /**
     * Returns a new node to be placed in a hash table bucket
     */
    private Node createNode(K key, V value) {
        return new Node(key, value);
    }

    /**
     * Returns a data structure to be a hash table bucket
     *
     * The only requirements of a hash table bucket are that we can:
     *  1. Insert items (`add` method)
     *  2. Remove items (`remove` method)
     *  3. Iterate through items (`iterator` method)
     *
     * Each of these methods is supported by java.util.Collection,
     * Most data structures in Java inherit from Collection, so we
     * can use almost any data structure as our buckets.
     *
     * Override this method to use different data structures as
     * the underlying bucket type
     *
     * BE SURE TO CALL THIS FACTORY METHOD INSTEAD OF CREATING YOUR
     * OWN BUCKET DATA STRUCTURES WITH THE NEW OPERATOR!
     */
    protected Collection<Node> createBucket() {
        return new LinkedList<>();
    }

    /**
     * Returns a table to back our hash table. As per the comment
     * above, this table can be an array of Collection objects
     *
     * BE SURE TO CALL THIS FACTORY METHOD WHEN CREATING A TABLE SO
     * THAT ALL BUCKET TYPES ARE OF JAVA.UTIL.COLLECTION
     *
     * @param tableSize the size of the table to create
     */
    private Collection<Node>[] createTable(int tableSize) {
        Collection[] table = new Collection[tableSize];
        for (int i = 0; i < table.length; i++) {
            table[i] = createBucket();
        }
        return (Collection<Node>[]) table;
    }

    // TODO: Implement the methods of the Map61B Interface below
    // Your code won't compile until you do so!


    @Override
    public void clear() {
        for (int i = 0; i < buckets.length; i++) {
            buckets[i].clear();
        }
        this.size = 0;
    }

    @Override
    public boolean containsKey(K key) {
        return getNode(key) != null;
    }

    private Node getNode(K key) {
        int index = hashIndex(key);
        Collection<Node> bucket= buckets[index];
        for (Node element : bucket) {
            if (element.key.equals(key)) {
                return element;
            }
        }
        return null;
    }

    @Override
    public V get(K key) {
        Node tmpNode = getNode(key);
        if (tmpNode == null){
            return null;
        } else {
            return tmpNode.value;
        }
    }

    @Override
    public int size() {
        return this.size;
    }

    private int hashIndex(K key) {
        return Math.floorMod(key.hashCode(), buckets.length);
    }
    private int hashIndex(K key, int length) {
        return Math.floorMod(key.hashCode(), length);
    }

    private void insertToTable(Collection<Node>[] table, Node element) {
        int index = hashIndex(element.key, table.length);
        Collection<Node> newBucket = table[index];
        newBucket.add(element);
    }
    @Override
    public void put(K key, V value) {
        Node tmpNode = getNode(key);
        if (tmpNode != null){
            tmpNode.value = value;
        } else {
            double currLoadFactor = (double) size /buckets.length;
            if (currLoadFactor > loadFactor) {
                int newSize = buckets.length * 2;
                Collection<Node>[] newTable = createTable(newSize);
                for (int i = 0; i < buckets.length; i ++ ) {
                    Collection<Node> bucket = buckets[i];
                    for (Node element : bucket) {
                        insertToTable(newTable, element);
                    }
                }
                buckets = newTable;
            }
            int index = hashIndex(key);
            Node newNode = createNode(key, value);
            Collection<Node> bucket = buckets[index];
            bucket.add(newNode);
            size += 1;
        }
    }

    @Override
    public Set<K> keySet() {
        Set<K> keySet = new HashSet<K>();
        for (int i = 0; i < buckets.length; i++) {
            Collection<Node> bucket = buckets[i];
            for (Node element : bucket) {
                keySet.add(element.key);
            }
        }
        return keySet;
    }

    @Override
    public V remove(K key) {
        if (containsKey(key)) {
            return remove(key, getNode(key).value);
        }
        return null;

    }

    @Override
    public V remove(K key, V value) {
        Node rmNode = getNode(key);
        if (rmNode == null) {
            return null;
        }
        if (rmNode.value.equals(value)) {
            buckets[hashIndex(key)].remove(rmNode);
            size = size - 1;
            return value;
        } else {
            return null;
        }
    }

    @Override
    public Iterator<K> iterator() {
        return keySet().iterator();
    }

}
