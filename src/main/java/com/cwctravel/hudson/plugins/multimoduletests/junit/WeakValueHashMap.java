package com.cwctravel.hudson.plugins.multimoduletests.junit;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A map that maintains weak references to its values.
 * 
 * @param <K>
 *        Key type.
 * @param <V>
 *        Value type.
 * @author Philip Aston
 */
public final class WeakValueHashMap<K, V> implements Map<K, V> {
	private final Map<K, WeakReference<V>> m_map = new HashMap<K, WeakReference<V>>();

	/**
	 * Clear all entries out of the map.
	 */
	public void clear() {
		m_map.clear();
	}

	/**
	 * Look up a value by key.
	 * 
	 * @param key
	 *        The key.
	 * @return The value, or <code>null</code> if none found.
	 */
	public V get(Object key) {
		final WeakReference<V> reference = m_map.get(key);
		return reference != null ? reference.get() : null;
	}

	/**
	 * Add a value.
	 * 
	 * @param key
	 *        The key.
	 * @param value
	 *        The value.
	 */
	public V put(K key, V value) {
		if(m_map.put(key, new WeakReference<V>(value)) != null) {
			return value;
		}
		return null;
	}

	/**
	 * Remove an entry from the map.
	 * 
	 * @param key
	 *        The key.
	 * @return The removed value, or <code>null</code> if none found.
	 */
	public V remove(Object key) {
		final WeakReference<V> reference = m_map.remove(key);
		return reference != null ? reference.get() : null;
	}

	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean containsKey(Object key) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean containsValue(Object value) {
		// TODO Auto-generated method stub
		return false;
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		// TODO Auto-generated method stub

	}

	public Set<K> keySet() {
		// TODO Auto-generated method stub
		return null;
	}

	public Collection<V> values() {
		// TODO Auto-generated method stub
		return null;
	}

	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return null;
	}
}
