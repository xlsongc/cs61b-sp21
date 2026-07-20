package bstmap;


import java.util.Iterator;
import java.util.Set;

public class BSTMap<K extends Comparable<K>, V> implements Map61B<K, V> {
    int size = 0;
    BSTNode root = null;

    @Override
    public Iterator<K> iterator() {
        return null;
    }

    private class BSTNode {
        K key;
        V val;
        BSTNode left;
        BSTNode right;

        BSTNode(K k, V v, BSTNode left, BSTNode right) {
            key = k;
            val = v;
            this.left = left;
            this.right = right;
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void put(K key, V value) {
        root = put(root, key, value);
    }

    private BSTNode put(BSTNode node, K key, V value) {
        if (node == null) {
            BSTNode newRoot = new BSTNode(key, value, null, null);
            node = newRoot;
            size = size + 1;
            return node;
        }
        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            node.left = put(node.left, key, value);
        } else if (cmp > 0) {
            node.right =  put(node.right, key, value);
        } else {
            node.val = value;
        }
        return node;
    }

    @Override
    public Set<K> keySet() {
        return Set.of();
    }


    @Override
    public void clear() {
        root = null;
        size = 0;

    }

    @Override
    public boolean containsKey(K key) {
        return containsKey(root, key) ;
    }

    private boolean containsKey(BSTNode node, K key) {
        if (node == null) {
            return false;
        }
        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            return containsKey(node.left, key);
        } else if (cmp > 0) {
            return containsKey(node.right, key);
        } else {
            return true;
        }
    }

    @Override
    public V get(K key) {
        return get(root, key);
    }

    private V get(BSTNode node, K key) {
        if (node == null) {
            return null;
        }
        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            return get(node.left, key);
        } else if (cmp > 0) {
            return get(node.right, key);
        } else {
            return node.val;
        }
    }

    @Override
    public V remove(K key) {
        throw new UnsupportedOperationException();
    }
    @Override
    public V remove(K key, V value) {
        throw new UnsupportedOperationException();
    }

}